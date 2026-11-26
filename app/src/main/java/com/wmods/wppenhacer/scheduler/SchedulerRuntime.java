package com.wmods.wppenhacer.scheduler;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.*;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.*;

/** Version-pinned experimental adapter using WhatsApp's own direct-reply service. */
public final class SchedulerRuntime {
    private static final String SERVICE="com.whatsapp.consumer.notification.DirectReplyService";
    private static final String RECEIVER="com.whatsapp.alarmservice.AlarmBroadcastReceiver";
    public static final String TICK="id.asharizki.watrobos.SCHEDULE_TICK";
    private static final AtomicBoolean installed=new AtomicBoolean();
    private static final AtomicBoolean checking=new AtomicBoolean();
    private static final AtomicBoolean checkAgain=new AtomicBoolean();
    private static PendingIntent alarm;
    private static volatile long armedNext;
    private static Context app;
    private static String hostVersion;
    private static Method original;
    private static Class<?> replyClass;
    private static Bundle call(String op,String id,Bundle data){return app.getContentResolver().call(ScheduleProvider.URI,op,id,data);}
    private static void health(String status){Bundle b=new Bundle();b.putString("bridge",status);try{call("health",null,b);}catch(Throwable ignored){}}
    public static void start(Application application,ClassLoader loader) {
        if(!installed.compareAndSet(false,true))return;app=application;
        try {
            String v=app.getPackageManager().getPackageInfo(app.getPackageName(),0).versionName;
            if(!"2.26.43.74".equals(v) && !"2.26.43.76".equals(v) && !"2.26.44.90".equals(v) && !"2.26.44.92".equals(v) && !"2.26.46.82".equals(v)){health("Tidak didukung: WhatsApp "+v+". Perlu pemeriksaan ulang.");return;}
            hostVersion=v;
            replyClass=loader.loadClass(SERVICE);original=replyClass.getDeclaredMethod("onHandleIntent",Intent.class);original.setAccessible(true);
            PendingIntent old=PendingIntent.getService(app,0x41534841,new Intent(TICK).setClassName(app.getPackageName(),SERVICE),PendingIntent.FLAG_NO_CREATE|PendingIntent.FLAG_IMMUTABLE);
            if(old!=null){((AlarmManager)app.getSystemService(Context.ALARM_SERVICE)).cancel(old);old.cancel();}
            Intent tick=new Intent(TICK).setClassName(app.getPackageName(),RECEIVER);
            alarm=PendingIntent.getBroadcast(app,0x41534841,tick,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            BroadcastReceiver refresh=new BroadcastReceiver(){public void onReceive(Context c,Intent i){refresh();}};
            IntentFilter filter=new IntentFilter(ScheduleProvider.CHANGED);
            if(Build.VERSION.SDK_INT>=33)app.registerReceiver(refresh,filter,"id.asharizki.watrobos.permission.SCHEDULE_REFRESH",null,Context.RECEIVER_EXPORTED);
            else app.registerReceiver(refresh,filter,"id.asharizki.watrobos.permission.SCHEDULE_REFRESH",null);
            health("Siap \u2022 WhatsApp "+v);refresh();
            // Recovers re-arming if the queue UI/provider was temporarily unavailable.
            Handler h=new Handler(Looper.getMainLooper());h.postDelayed(new Runnable(){public void run(){refresh();h.postDelayed(this,30000);}},30000);
            Log.i("WaTrobosScheduler", "[WaTrobos Scheduler] receiver adapter v3 ready");
        }catch(Throwable t){health("Jembatan gagal: "+t.getClass().getSimpleName());Log.i("WaTrobosScheduler", "[WaTrobos Scheduler] install: "+t.getClass().getSimpleName());}
    }
    public static void onTick(BroadcastReceiver receiver) {
        if (original == null || alarm == null) return;
        armedNext=0;
        BroadcastReceiver.PendingResult pending=receiver.goAsync();
        new Thread(()->{try{dispatch();}catch(Throwable t){Log.e("WaTrobosScheduler","Tick failed",t);}
            finally{if(pending!=null)pending.finish();refresh();}},"watrobos-schedule-send").start();
    }
    private static void refresh() {
        if(alarm==null)return;
        if(!checking.compareAndSet(false,true)){checkAgain.set(true);return;}
        new Thread(()->{try{
            // Reconcile uncertain results by reading existing outgoing rows only.
            // This operation never invokes the sender or retries a message.
            JSONArray uncertain=new JSONArray(call("unconfirmed",null,null).getString("jobs","[]"));
            for(int n=0;n<uncertain.length();n++) {
                JSONObject row=uncertain.getJSONObject(n);long at=row.getLong("at");
                if(findOutgoing(row.getString("phone"),row.getString("message"),at-10000,at+10000))
                    finish(row.getString("id"),"QUEUED","Pesan keluar ditemukan di WhatsApp (termasuk pemetaan LID). Status diterima belum dipastikan.");
            }
            Bundle b=call("peek",null,null);AlarmManager am=(AlarmManager)app.getSystemService(Context.ALARM_SERVICE);
            long next=b.getLong("next",0);if(!b.getBoolean("enabled")||next==0){am.cancel(alarm);armedNext=0;return;}
            if(armedNext==next)return;
            long when=Math.max(System.currentTimeMillis()+1000,next);
            boolean exact=Build.VERSION.SDK_INT<31||am.canScheduleExactAlarms();
            if(exact)am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,alarm);
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,alarm);
            armedNext=next;
            health(exact?"Siap \u2022 alarm presisi \u2022 WA "+hostVersion:"Siap \u2022 alarm perkiraan \u2022 WA "+hostVersion);
        }catch(Throwable t){health("Penjadwalan gagal: "+t.getClass().getSimpleName());}finally{
            syncContacts();checking.set(false);if(checkAgain.getAndSet(false))refresh();
        }},"asha-schedule-arm").start();
    }
    private static void syncContacts() {
        String token=null;
        try {
            Bundle request=call("contacts_pending",null,null);token=request.getString("token");if(token==null)return;
            String query=request.getString("query","");JSONArray rows=new JSONArray();
            java.util.Map<String,String> savedNames=DeviceContacts.names(app);
            SharedPreferences identity=app.getSharedPreferences("com.whatsapp_preferences_light",Context.MODE_PRIVATE);
            String selfPhone=identity.getString("registration_jid","").split("@")[0];
            Object storedLid=identity.getAll().get("self_lid");String selfLid=storedLid==null?"":storedLid.toString();
            if(selfLid.matches("[1-9][0-9]{5,20}"))selfLid+="@lid";
            if(!selfLid.matches("[1-9][0-9]{5,20}@lid")||!selfPhone.matches("[1-9][0-9]{6,14}")){selfLid="";selfPhone="";}
            java.util.Set<String> seen=new java.util.HashSet<>();int photoBudget=140000;
            try(SQLiteDatabase contacts=SQLiteDatabase.openDatabase(app.getDatabasePath("wa.db").getPath(),null,SQLiteDatabase.OPEN_READONLY);
                SQLiteDatabase messages=SQLiteDatabase.openDatabase(app.getDatabasePath("msgstore.db").getPath(),null,SQLiteDatabase.OPEN_READONLY);
                Cursor c=messages.rawQuery("SELECT CASE WHEN j.server='s.whatsapp.net' THEN j.raw_string WHEN j.raw_string=? THEN ? ELSE pn.raw_string END AS recipient,COALESCE(m.timestamp,c.sort_timestamp,0) AS latest,COALESCE(m.text_data,''),COALESCE(m.message_type,0),COALESCE(m.from_me,0),j.raw_string FROM chat c JOIN jid j ON j._id=c.jid_row_id LEFT JOIN jid_map map ON map.lid_row_id=j._id LEFT JOIN jid pn ON pn._id=map.jid_row_id LEFT JOIN message m ON m._id=c.last_message_row_id WHERE c.last_message_row_id>0 AND COALESCE(c.hidden,0)=0 AND COALESCE(c.chat_lock,0)=0 AND COALESCE(c.archived,0)=0 AND (j.server='s.whatsapp.net' OR pn.server='s.whatsapp.net' OR j.raw_string=?) ORDER BY latest DESC LIMIT 500",new String[]{selfLid,selfPhone+"@s.whatsapp.net",selfLid})) {
                while(c.moveToNext()&&rows.length()<100) {
                    String jid=c.getString(0);if(jid==null||!jid.endsWith("@s.whatsapp.net")||!seen.add(jid))continue;
                    String phone=jid.substring(0,jid.indexOf('@'));if(!phone.matches("[1-9][0-9]{6,14}"))continue;
                    boolean saved=savedNames.containsKey(phone);String name=savedNames.get(phone);
                    if(!saved)try(Cursor contact=contacts.rawQuery("SELECT COALESCE(NULLIF(display_name,''),NULLIF(wa_name,''),'') FROM wa_contacts WHERE jid=? LIMIT 1",new String[]{jid})){name=contact.moveToFirst()?contact.getString(0):"";}
                    if(phone.equals(selfPhone)){name=(name==null||name.isEmpty()?"Anda":name)+" (Anda)";}
                    if(name==null)name="";if(!DeviceContacts.matches(name,phone,phone,query))continue;
                    JSONObject row=new JSONObject();row.put("phone",phone);row.put("name",name);row.put("recent",c.getLong(1));row.put("saved",saved);
                    row.put("preview",ChatPreview.message(c.getString(2),c.getInt(3),c.getInt(4)!=0));
                    String photo=ChatPreview.avatar(app,jid,c.getString(5),phone.equals(selfPhone));
                    if(photo.length()<=photoBudget){row.put("avatar",photo);photoBudget-=photo.length();}
                    rows.put(row);
                }
            }
            Bundle result=new Bundle();result.putString("token",token);result.putString("rows",rows.toString());call("contacts_publish",null,result);
        }catch(Throwable t){
            if(token!=null)try{Bundle error=new Bundle();error.putString("token",token);error.putBoolean("error",true);call("contacts_publish",null,error);}catch(Throwable ignored){}
            Log.i("WaTrobosScheduler", "[WaTrobos Scheduler] contacts: "+t.getClass().getSimpleName());
        }
    }
    private static void finish(String id,String state,String detail){Bundle b=new Bundle();b.putString("state",state);b.putString("detail",detail);call("result",id,b);}
    private static boolean findOutgoing(String phone,String text,long from,long until) {
        String jid=phone+"@s.whatsapp.net";
        // WhatsApp omits its own PN/LID pair from jid_map. Resolve only the
        // registered account's self-chat, and only when the target phone matches.
        String selfJid="";
        SharedPreferences identity=app.getSharedPreferences("com.whatsapp_preferences_light",Context.MODE_PRIVATE);
        String registered=identity.getString("registration_jid","").split("@")[0];
        if(phone.equals(registered)) {
            Object stored=identity.getAll().get("self_lid");
            String lid=stored==null?"":stored.toString();
            if(lid.matches("[1-9][0-9]{5,20}"))selfJid=lid+"@lid";
            else if(lid.matches("[1-9][0-9]{5,20}@lid"))selfJid=lid;
        }
        try(SQLiteDatabase db=SQLiteDatabase.openDatabase(app.getDatabasePath("msgstore.db").getPath(),null,SQLiteDatabase.OPEN_READONLY);
            Cursor c=db.rawQuery("SELECT 1 FROM message m JOIN chat c ON c._id=m.chat_row_id JOIN jid j ON j._id=c.jid_row_id WHERE m.from_me=1 AND m.text_data=? AND m.timestamp BETWEEN ? AND ? AND (j.raw_string=? OR j.raw_string=? OR EXISTS (SELECT 1 FROM jid_map map JOIN jid pn ON pn._id=map.jid_row_id WHERE map.lid_row_id=j._id AND pn.raw_string=?)) LIMIT 1",new String[]{text,Long.toString(from),Long.toString(until),jid,selfJid,jid})) {return c.moveToFirst();}
    }
    private static void dispatch() throws Throwable {
        Bundle job=call("claim",null,null);String id=job.getString("id");if(id==null)return;
        boolean attempted=false;
        try {
            String phone=job.getString("phone"),text=job.getString("message");long contactId;
            try(SQLiteDatabase db=SQLiteDatabase.openDatabase(app.getDatabasePath("wa.db").getPath(),null,SQLiteDatabase.OPEN_READONLY);
                Cursor c=db.rawQuery("SELECT _id FROM wa_contacts WHERE jid=? AND is_whatsapp_user=1 LIMIT 1",new String[]{phone+"@s.whatsapp.net"})) {
                if(!c.moveToFirst()){finish(id,"FAILED","Nomor belum dikenal WhatsApp. Buka chat tujuan lebih dahulu.");return;}contactId=c.getLong(0);
            }
            Intent send=new Intent(app.getPackageName()+".intent.action.DIRECT_REPLY_FROM_MESSAGE").setClassName(app.getPackageName(),SERVICE)
                .setData(Uri.parse("content://"+app.getPackageName()+".provider.contact/contacts/"+contactId));
            Bundle input=new Bundle();input.putCharSequence("direct_reply_input",text);
            RemoteInput.addResultsToIntent(new RemoteInput[]{new RemoteInput.Builder("direct_reply_input").build()},send,input);
            long start=System.currentTimeMillis();
            // Re-check the switch after resolution, before invoking the native sender.
            if(!call("peek",null,null).getBoolean("enabled")){finish(id,"FAILED","Fitur dinonaktifkan sebelum pengiriman.");return;}
            // Reuse the native reply handler on a context-attached instance, without
            // starting an Android background Service or its lifecycle/handler thread.
            Object service=replyClass.getDeclaredConstructor().newInstance();
            replyClass.getMethod("attachBaseContext",Context.class).invoke(service,app);
            attempted=true;original.invoke(service,send);
            // Verify a local outgoing row, without treating this as server delivery.
            boolean queued=findOutgoing(phone,text,start-1000,System.currentTimeMillis()+1000);
            finish(id,queued?"QUEUED":"UNKNOWN",queued?"Pesan tercatat di antrean WhatsApp; status diterima belum dipastikan.":"Pemanggilan pengiriman selesai; periksa chat. Tidak dicoba ulang otomatis.");
            Log.i("WaTrobosScheduler", "[WaTrobos Scheduler] job result: "+(queued?"QUEUED":"UNKNOWN"));
        }catch(Throwable t){finish(id,attempted?"UNKNOWN":"FAILED","Gangguan "+t.getClass().getSimpleName()+". Periksa chat sebelum mencoba lagi.");}
    }
}
