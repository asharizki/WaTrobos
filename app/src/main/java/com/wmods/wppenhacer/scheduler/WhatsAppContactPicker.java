package com.wmods.wppenhacer.scheduler;

import android.app.*;
import android.content.*;
import android.os.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.util.Base64;
import android.util.LruCache;
import java.util.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.ArrayList;

/** On-demand search; selection fills the editor and never schedules or sends. */
final class WhatsAppContactPicker {
    interface Picked { void select(String phone,String name); }
    private final Activity activity;
    private final Picked picked;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ArrayList<JSONObject> rows=new ArrayList<>();
    private final LruCache<String,Bitmap> thumbnails=new LruCache<>(40);
    private boolean showingPhoneContacts=true;
    private EditText search;
    private TextView info;
    private BaseAdapter adapter;
    private AlertDialog dialog;
    private String token="";
    private long deadline;
    private boolean closed;
    private boolean phoneSource=true;
    private final Runnable debounce=()->request();
    private final Runnable poll=new Runnable(){public void run(){pollResult();}};
    private WhatsAppContactPicker(Activity a,Picked p){activity=a;picked=p;}
    static void show(Activity a,Picked p){new WhatsAppContactPicker(a,p).open();}
    private Bundle call(String op,Bundle b){return activity.getContentResolver().call(ScheduleProvider.URI,op,null,b);}
    private int dp(int v){return Math.round(v*activity.getResources().getDisplayMetrics().density);}
    private void open(){
        LinearLayout content=new LinearLayout(activity);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(16),0,dp(16),0);
        RadioGroup sources=new RadioGroup(activity);sources.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton phone=new RadioButton(activity);phone.setId(View.generateViewId());phone.setText("Kontak HP");
        RadioButton recent=new RadioButton(activity);recent.setId(View.generateViewId());recent.setText("Chat terbaru");
        sources.addView(phone);sources.addView(recent);sources.check(phone.getId());content.addView(sources);
        search=new EditText(activity);search.setSingleLine(true);search.setHint("Cari nama atau nomor");content.addView(search);
        info=new TextView(activity);info.setPadding(0,dp(8),0,dp(8));content.addView(info);
        ListView list=new ListView(activity);
        adapter=new BaseAdapter(){
            public int getCount(){return rows.size();}
            public JSONObject getItem(int position){return rows.get(position);}
            public long getItemId(int position){return position;}
            public View getView(int position,View recycled,ViewGroup parent){
                ChatRow view=recycled instanceof ChatRow?(ChatRow)recycled:new ChatRow();view.bind(getItem(position));return view;
            }
        };
        list.setAdapter(adapter);list.setDivider(new ColorDrawable(0x223c4b60));list.setDividerHeight(dp(1));
        content.addView(list,new LinearLayout.LayoutParams(-1,dp(360)));
        list.setOnItemClickListener((p,v,pos,id)->{
            if(pos<0||pos>=rows.size())return;JSONObject row=rows.get(pos);picked.select(row.optString("phone"),row.optString("name"));dialog.dismiss();
        });
        dialog=new AlertDialog.Builder(activity).setTitle("Pilih tujuan pesan").setView(content)
            .setPositiveButton("Perbarui",null).setNeutralButton("Buka WhatsApp",null).setNegativeButton("Tutup",null).create();
        dialog.setOnDismissListener(d->{closed=true;handler.removeCallbacksAndMessages(null);clearRemotePreview();rows.clear();thumbnails.evictAll();});
        dialog.setOnShowListener(d->{
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                if(phoneSource&&!DeviceContacts.permitted(activity)){
                    activity.requestPermissions(new String[]{android.Manifest.permission.READ_CONTACTS},8801);
                    final long until=System.currentTimeMillis()+30000;
                    handler.postDelayed(new Runnable(){public void run(){if(closed||!phoneSource)return;if(DeviceContacts.permitted(activity))request();else if(System.currentTimeMillis()<until)handler.postDelayed(this,500);}},500);
                }else request();
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{
                Intent i=activity.getPackageManager().getLaunchIntentForPackage("com.whatsapp");
                if(i!=null){activity.startActivity(i);info.setText("Kembali ke halaman ini setelah WhatsApp terbuka, lalu tekan Perbarui.");}
            });request();
        });
        search.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){handler.removeCallbacks(debounce);handler.postDelayed(debounce,300);}
            public void afterTextChanged(Editable s){}
        });sources.setOnCheckedChangeListener((group,id)->{phoneSource=id==phone.getId();request();});dialog.show();
    }
    private void request(){
        if(closed)return;handler.removeCallbacks(poll);handler.removeCallbacks(debounce);
        clearRemotePreview();rows.clear();thumbnails.evictAll();adapter.notifyDataSetChanged();token=java.util.UUID.randomUUID().toString();
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setVisibility(phoneSource?View.GONE:View.VISIBLE);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText("Perbarui");
        if(phoneSource){
            if(!DeviceContacts.permitted(activity)){info.setText("Izinkan akses Kontak untuk menampilkan nama dan nomor yang Anda simpan di HP.");dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText("Izinkan kontak");return;}
            info.setText("Membaca kontak yang tersimpan di HP\u2026");final String expected=token,query=search.getText().toString();
            new Thread(()->{try{JSONArray data=DeviceContacts.search(activity,query,100);handler.post(()->{if(!closed&&phoneSource&&expected.equals(token))display(data,true);});}
                catch(Exception e){handler.post(()->{if(!closed&&expected.equals(token))info.setText("Kontak HP belum dapat dibaca. Periksa izin Kontak lalu tekan Perbarui.");});}},"asha-phone-contacts").start();return;
        }
        info.setText("Membaca percakapan terbaru WhatsApp\u2026");
        try{Bundle b=new Bundle();b.putString("query",search.getText().toString());Bundle result=call("contacts_request",b);token=result.getString("token","");deadline=System.currentTimeMillis()+10000;handler.postDelayed(poll,300);}
        catch(RuntimeException e){info.setText("Tidak dapat meminta kontak. Buka WhatsApp lalu tekan Perbarui.");}
    }
    private void pollResult(){
        if(closed)return;
        try{
            Bundle result=call("contacts_result",null);String status=result.getString("status","");
            if(token.equals(result.getString("token"))&&"ready".equals(status)){
                display(new JSONArray(result.getString("rows","[]")),false);return;
            }
            if(token.equals(result.getString("token"))&&"error".equals(status)){info.setText("Kontak belum dapat dibaca. Buka WhatsApp lalu tekan Perbarui.");return;}
            if(System.currentTimeMillis()>deadline){info.setText("WhatsApp belum merespons. Tekan Buka WhatsApp, kembali ke sini, lalu Perbarui.");return;}
            handler.postDelayed(poll,400);
        }catch(Exception e){info.setText("Kontak belum dapat dibaca. Buka WhatsApp lalu tekan Perbarui.");}
    }
    private void clearRemotePreview(){
        if(token.isEmpty())return;
        try{Bundle b=new Bundle();b.putString("token",token);call("contacts_clear",b);}catch(RuntimeException ignored){}
    }
    private void display(JSONArray data,boolean fromPhone){
        rows.clear();showingPhoneContacts=fromPhone;
        for(int i=0;i<data.length();i++){
            JSONObject row=data.optJSONObject(i);if(row==null)continue;
            if(fromPhone&&row.optString("name","").trim().isEmpty())continue;
            rows.add(row);
        }
        adapter.notifyDataSetChanged();
        String source=fromPhone?"kontak HP bernama":"chat pribadi terbaru";
        info.setText(rows.isEmpty()?"Tidak ada "+source+" yang cocok.":rows.size()+" "+source+" ditampilkan (maksimal 100 hasil)."+(fromPhone?"":" Urutan paling baru."));
    }
    private String chatTime(long at){
        if(at<=0)return "";
        if(android.text.format.DateUtils.isToday(at))return new java.text.SimpleDateFormat("HH:mm",Locale.getDefault()).format(new Date(at));
        Calendar yesterday=Calendar.getInstance();yesterday.add(Calendar.DAY_OF_YEAR,-1);
        Calendar when=Calendar.getInstance();when.setTimeInMillis(at);
        if(yesterday.get(Calendar.YEAR)==when.get(Calendar.YEAR)&&yesterday.get(Calendar.DAY_OF_YEAR)==when.get(Calendar.DAY_OF_YEAR))return "Kemarin";
        return new java.text.SimpleDateFormat("dd/MM/yy",Locale.getDefault()).format(new Date(at));
    }
    private String initials(String name){
        StringBuilder value=new StringBuilder();
        for(String word:name.trim().split("\\s+")){
            if(word.isEmpty()||word.startsWith("("))continue;
            int point=word.codePointAt(0);if(Character.isLetter(point))value.appendCodePoint(Character.toUpperCase(point));
            if(value.codePointCount(0,value.length())>=2)break;
        }
        return value.length()==0?"WA":value.toString();
    }
    private TextView label(int size,int color){TextView t=new TextView(activity);t.setTextSize(size);t.setTextColor(color);t.setSingleLine(true);t.setEllipsize(TextUtils.TruncateAt.END);return t;}
    private final class ChatRow extends LinearLayout {
        final TextView name,preview,time,initial;
        final ImageView photo;
        ChatRow(){
            super(activity);setOrientation(HORIZONTAL);setGravity(Gravity.CENTER_VERTICAL);setPadding(0,dp(10),0,dp(10));
            setLayoutParams(new AbsListView.LayoutParams(-1,dp(80)));
            FrameLayout avatar=new FrameLayout(activity);
            GradientDrawable circle=new GradientDrawable();circle.setShape(GradientDrawable.OVAL);circle.setColor(0xff284159);
            avatar.setBackground(circle);avatar.setClipToOutline(true);
            initial=label(17,0xffd5e4f6);initial.setGravity(Gravity.CENTER);initial.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
            avatar.addView(initial,new FrameLayout.LayoutParams(-1,-1));
            photo=new ImageView(activity);photo.setScaleType(ImageView.ScaleType.CENTER_CROP);avatar.addView(photo,new FrameLayout.LayoutParams(-1,-1));
            addView(avatar,new LinearLayout.LayoutParams(dp(52),dp(52)));
            LinearLayout text=new LinearLayout(activity);text.setOrientation(VERTICAL);text.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams textParams=new LinearLayout.LayoutParams(0,-1,1);textParams.leftMargin=dp(12);addView(text,textParams);
            LinearLayout heading=new LinearLayout(activity);heading.setGravity(Gravity.CENTER_VERTICAL);
            name=label(15,0xfff0f4fa);name.setTypeface(Typeface.DEFAULT,Typeface.BOLD);heading.addView(name,new LinearLayout.LayoutParams(0,-2,1));
            time=label(10,0xffb0bccd);time.setGravity(Gravity.END);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-2,-2);tp.leftMargin=dp(8);heading.addView(time,tp);text.addView(heading);
            preview=label(13,0xffb0bccd);LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,-2);pp.topMargin=dp(6);text.addView(preview,pp);
        }
        void bind(JSONObject row){
            String title=row.optString("name","").trim(),phone=row.optString("phone","");if(title.isEmpty())title="+"+phone;
            name.setText(title);initial.setText(initials(title));
            String last=showingPhoneContacts?"+"+phone:row.optString("preview","");if(last.isEmpty())last="Pesan nonteks";
            preview.setText(last);time.setText(showingPhoneContacts?"":chatTime(row.optLong("recent")));
            String encoded=showingPhoneContacts?"":row.optString("avatar","");Bitmap bitmap=null;
            if(!encoded.isEmpty())try{
                bitmap=thumbnails.get(encoded);
                if(bitmap==null){byte[] bytes=Base64.decode(encoded,Base64.NO_WRAP);bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length);if(bitmap!=null)thumbnails.put(encoded,bitmap);}
            }catch(RuntimeException ignored){}
            photo.setImageBitmap(bitmap);photo.setVisibility(bitmap==null?View.GONE:View.VISIBLE);photo.setContentDescription(bitmap==null?null:"Foto profil");
            setContentDescription(title+", "+last+(time.getText().length()==0?"":", "+time.getText()));
        }
    }
}
