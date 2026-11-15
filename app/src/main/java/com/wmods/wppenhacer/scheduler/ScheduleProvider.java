package com.wmods.wppenhacer.scheduler;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import android.net.Uri;
import android.os.*;
import org.json.*;

/** Private queue, shared only with the installed official WhatsApp UID. */
public final class ScheduleProvider extends ContentProvider {
    public static final Uri URI = Uri.parse("content://id.asharizki.watrobos.scheduler");
    public static final String CHANGED = "id.asharizki.watrobos.SCHEDULE_CHANGED";
    private SQLiteOpenHelper helper;
    // Contact search is on demand; do not persist the WhatsApp address book.
    private String contactToken="",contactQuery="",contactStatus="idle",contactRows="[]";
    private long contactDeadline;
    @Override public boolean onCreate() {
        helper = new SQLiteOpenHelper(getContext(), "asha_schedule.db", null, 1) {
            public void onCreate(SQLiteDatabase db) {
                db.execSQL("CREATE TABLE jobs(id TEXT PRIMARY KEY,phone TEXT NOT NULL,message TEXT NOT NULL,due INTEGER NOT NULL,state TEXT NOT NULL,detail TEXT NOT NULL DEFAULT '',updated INTEGER NOT NULL)");
                db.execSQL("CREATE TABLE settings(k TEXT PRIMARY KEY,v TEXT)");
                db.execSQL("INSERT INTO settings VALUES('enabled','0')");
            }
            public void onUpgrade(SQLiteDatabase db,int a,int b) { throw new IllegalStateException("Unsupported schema"); }
        };
        return true;
    }
    private boolean owner() { return Binder.getCallingUid() == android.os.Process.myUid(); }
    private void authorize() {
        if (owner()) return;
        try {
            int uid = getContext().getPackageManager().getApplicationInfo("com.whatsapp",0).uid;
            if (Binder.getCallingUid() == uid) return;
        } catch (Exception ignored) {}
        throw new SecurityException("Scheduler access denied");
    }
    private String setting(SQLiteDatabase db,String key,String def) {
        try(Cursor c=db.rawQuery("SELECT v FROM settings WHERE k=?",new String[]{key})) {return c.moveToFirst()?c.getString(0):def;}
    }
    private void put(SQLiteDatabase db,String key,String val) {
        ContentValues v=new ContentValues();v.put("k",key);v.put("v",val);db.insertWithOnConflict("settings",null,v,SQLiteDatabase.CONFLICT_REPLACE);
    }
    private void signal() {
        Intent i=new Intent(CHANGED).setPackage("com.whatsapp");getContext().sendBroadcast(i);
    }
    @Override public synchronized Bundle call(String method,String arg,Bundle extras) {
        authorize(); boolean own=owner(); Bundle out=new Bundle();
        if(method.startsWith("contacts_"))return contacts(method,own,extras==null?new Bundle():extras);
        SQLiteDatabase db=helper.getWritableDatabase();long now=System.currentTimeMillis();
        ContentValues interrupted=new ContentValues();interrupted.put("state","UNKNOWN");interrupted.put("detail","Proses terputus. Periksa chat sebelum membuat jadwal baru.");
        db.update("jobs",interrupted,"state='PROCESSING' AND updated<?",new String[]{Long.toString(now-120000)});
        if (extras==null) extras=new Bundle();
        boolean enabled="1".equals(setting(db,"enabled","0"));
        if ("enable".equals(method)) {
            if(!own)throw new SecurityException();put(db,"enabled",extras.getBoolean("enabled")?"1":"0");signal();return out;
        }
        if ("save".equals(method)) {
            if(!own)throw new SecurityException();
            String phone=extras.getString("phone","").replaceAll("[+ ()-]", "");
            String msg=extras.getString("message","");long due=extras.getLong("due");
            if(!phone.matches("[1-9][0-9]{6,14}")||msg.trim().isEmpty()||msg.length()>4096||due<now+5000)throw new IllegalArgumentException("Periksa nomor, pesan, dan waktu (minimal 5 detik lagi).");
            String id=extras.getString("id");ContentValues v=new ContentValues();v.put("phone",phone);v.put("message",msg);v.put("due",due);v.put("state","WAITING");v.put("detail","");v.put("updated",now);
            if(id==null){id=java.util.UUID.randomUUID().toString();v.put("id",id);db.insertOrThrow("jobs",null,v);}
            else if(db.update("jobs",v,"id=? AND state='WAITING'",new String[]{id})!=1)throw new IllegalStateException("Jadwal sudah diproses; buat jadwal baru.");
            out.putString("id",id);signal();return out;
        }
        if ("cancel".equals(method)||"delete".equals(method)) {
            if(!own)throw new SecurityException();
            if("delete".equals(method))db.delete("jobs","id=? AND state NOT IN ('PROCESSING','WAITING')",new String[]{arg});
            else {ContentValues v=new ContentValues();v.put("state","CANCELLED");v.put("updated",now);db.update("jobs",v,"id=? AND state='WAITING'",new String[]{arg});}
            signal();return out;
        }
        if ("health".equals(method)) {
            if(own)throw new SecurityException();put(db,"bridge",extras.getString("bridge",""));put(db,"seen",Long.toString(now));return out;
        }
        if ("claim".equals(method)) {
            if(own)throw new SecurityException();
            if(!enabled)return out;
            db.beginTransaction();try {
                // An uncertain send is never automatically retried.
                ContentValues stale=new ContentValues();stale.put("state","UNKNOWN");stale.put("detail","Proses terputus. Periksa chat sebelum membuat jadwal baru.");
                db.update("jobs",stale,"state='PROCESSING' AND updated<?",new String[]{Long.toString(now-120000)});
                try(Cursor c=db.rawQuery("SELECT id,phone,message,due FROM jobs WHERE state='WAITING' AND due<=? ORDER BY due LIMIT 1",new String[]{Long.toString(now)})) {
                    if(c.moveToFirst()) {
                        String id=c.getString(0);ContentValues v=new ContentValues();
                        if(now-c.getLong(3)>15*60000){v.put("state","MISSED");v.put("detail","Jadwal lewat lebih dari 15 menit; tidak dikirim otomatis.");}
                        else {v.put("state","PROCESSING");out.putString("id",id);out.putString("phone",c.getString(1));out.putString("message",c.getString(2));}
                        v.put("updated",now);db.update("jobs",v,"id=? AND state='WAITING'",new String[]{id});
                    }
                }db.setTransactionSuccessful();
            }finally{db.endTransaction();}return out;
        }
        if ("result".equals(method)) {
            if(own)throw new SecurityException();String state=extras.getString("state", "UNKNOWN");
            if(!java.util.Arrays.asList("QUEUED","FAILED","UNKNOWN").contains(state))throw new IllegalArgumentException();
            ContentValues v=new ContentValues();v.put("state",state);v.put("detail",extras.getString("detail",""));v.put("updated",now);
            db.update("jobs",v,"id=? AND "+("QUEUED".equals(state)?"state IN ('PROCESSING','UNKNOWN')":"state='PROCESSING'"),new String[]{arg});return out;
        }
        if("unconfirmed".equals(method)) {
            if(own)throw new SecurityException();JSONArray rows=new JSONArray();
            try(Cursor c=db.rawQuery("SELECT id,phone,message,updated FROM jobs WHERE state='UNKNOWN' ORDER BY updated DESC LIMIT 20",null)) {
                while(c.moveToNext()){JSONObject row=new JSONObject();try{row.put("id",c.getString(0));row.put("phone",c.getString(1));row.put("message",c.getString(2));row.put("at",c.getLong(3));}catch(JSONException e){throw new IllegalStateException(e);}rows.put(row);}
            }out.putString("jobs",rows.toString());return out;
        }
        out.putBoolean("enabled",enabled);out.putString("bridge",setting(db,"bridge","Belum terhubung. Buka WhatsApp."));out.putLong("seen",Long.parseLong(setting(db,"seen","0")));
        try(Cursor c=db.rawQuery("SELECT MIN(due) FROM jobs WHERE state='WAITING'",null)){if(c.moveToFirst()&&!c.isNull(0))out.putLong("next",c.getLong(0));}
        if("list".equals(method)) {
            if(!own)throw new SecurityException();JSONArray rows=new JSONArray();
            try(Cursor c=db.rawQuery("SELECT id,phone,message,due,state,detail FROM jobs ORDER BY due DESC LIMIT 200",null)) {
                while(c.moveToNext()){JSONObject row=new JSONObject();try{row.put("id",c.getString(0));row.put("phone",c.getString(1));row.put("message",c.getString(2));row.put("due",c.getLong(3));row.put("state",c.getString(4));row.put("detail",c.getString(5));}catch(JSONException e){throw new IllegalStateException(e);}rows.put(row);}
            }out.putString("jobs",rows.toString());
        } else if(!"peek".equals(method))throw new IllegalArgumentException("Unknown method");
        return out;
    }
    private Bundle contacts(String method,boolean own,Bundle data) {
        Bundle out=new Bundle();long now=System.currentTimeMillis();
        if("contacts_clear".equals(method)) {
            if(!own)throw new SecurityException();
            if(contactToken.equals(data.getString("token"))){contactToken="";contactRows="[]";contactStatus="idle";contactDeadline=0;}
            return out;
        }
        if("contacts_request".equals(method)) {
            if(!own)throw new SecurityException();
            contactQuery=data.getString("query","").trim();if(contactQuery.length()>80)contactQuery=contactQuery.substring(0,80);
            contactToken=java.util.UUID.randomUUID().toString();contactDeadline=now+60000;contactStatus="loading";contactRows="[]";
            out.putString("token",contactToken);signal();return out;
        }
        if("contacts_result".equals(method)) {
            if(!own)throw new SecurityException();
            if(now>contactDeadline){contactRows="[]";contactStatus="expired";}
            out.putString("token",contactToken);out.putString("status",contactStatus);out.putString("rows",contactRows);return out;
        }
        if("contacts_pending".equals(method)) {
            if(own)throw new SecurityException();
            if("loading".equals(contactStatus)&&now<=contactDeadline){out.putString("token",contactToken);out.putString("query",contactQuery);}return out;
        }
        if("contacts_publish".equals(method)) {
            if(own)throw new SecurityException();
            if(!contactToken.equals(data.getString("token"))||now>contactDeadline)return out;
            String rows=data.getString("rows","[]");if(rows.length()>300000)throw new IllegalArgumentException();
            try {
                JSONArray input=new JSONArray(rows),clean=new JSONArray();int photoBudget=140000;if(input.length()>100)throw new IllegalArgumentException();
                for(int i=0;i<input.length();i++) {
                    JSONObject row=input.getJSONObject(i);String phone=row.getString("phone"),name=row.optString("name","");
                    if(!phone.matches("[1-9][0-9]{6,14}"))continue;
                    name=name.replaceAll("[\\p{Cntrl}]", " ");if(name.length()>160)name=name.substring(0,160);
                    JSONObject item=new JSONObject();item.put("phone",phone);item.put("name",name);item.put("recent",row.optLong("recent",0));item.put("saved",row.optBoolean("saved",false));
                    String preview=row.optString("preview","").replaceAll("[\\p{Cntrl}]"," ");if(preview.length()>180)preview=preview.substring(0,180);item.put("preview",preview);
                    String avatar=row.optString("avatar","");
                    if(avatar.length()<=2560&&avatar.length()<=photoBudget&&avatar.matches("[A-Za-z0-9+/=]*")){item.put("avatar",avatar);photoBudget-=avatar.length();}
                    clean.put(item);
                }
                contactRows=clean.toString();contactStatus=data.getBoolean("error")?"error":"ready";
            }catch(JSONException e){throw new IllegalArgumentException("Invalid contacts",e);}return out;
        }
        throw new IllegalArgumentException("Unknown contact operation");
    }
    @Override public Cursor query(Uri u,String[] p,String s,String[] a,String sort){throw new SecurityException();}
    @Override public String getType(Uri u){return null;}
    @Override public Uri insert(Uri u,ContentValues v){throw new SecurityException();}
    @Override public int update(Uri u,ContentValues v,String s,String[] a){throw new SecurityException();}
    @Override public int delete(Uri u,String s,String[] a){throw new SecurityException();}
}
