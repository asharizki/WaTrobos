package com.wmods.wppenhacer.scheduler;

import android.app.*;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.os.*;
import android.text.InputType;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.text.DateFormat;
import java.util.*;

public final class ScheduleActivity extends Activity {
    private LinearLayout body;
    private ScrollView scroll;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private boolean resumed;
    private boolean editing;
    private String rendered="";
    private int fg,bg,secondary;
    private final int green=0xffff8a3d,surface=0xff142238,edge=0xff30435e;
    private int filter;
    private volatile boolean loadingNames;
    private Map<String,String> names=new HashMap<>();
    private final Runnable refresh=new Runnable(){public void run(){if(resumed){render();handler.postDelayed(this,5000);}}};
    private Bundle call(String op,String id,Bundle b){return getContentResolver().call(ScheduleProvider.URI,op,id,b);}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    @Override public void onCreate(Bundle state){
        int theme=getResources().getIdentifier("WaTrobosSchedulerTheme","style",getPackageName());
        setTheme(theme==0?android.R.style.Theme_Material_NoActionBar:theme);
        super.onCreate(state);fg=0xffe9edef;bg=0xff0c1626;secondary=0xffaebac1;
        filter=getSharedPreferences("asha_schedule_ui",0).getInt("filter",0);if(filter<0||filter>4)filter=0;
        getWindow().setStatusBarColor(bg);getWindow().setNavigationBarColor(bg);
        getWindow().getDecorView().setSystemUiVisibility(0);
        LinearLayout root=column();root.setBackgroundColor(bg);
        root.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(0,i.getSystemWindowInsetTop(),0,i.getSystemWindowInsetBottom());return i.consumeSystemWindowInsets();});
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(dp(8),dp(6),dp(16),dp(6));
        TextView back=label("\u2039",32);back.setGravity(Gravity.CENTER);back.setContentDescription("Kembali");back.setOnClickListener(v->finish());bar.addView(back,new LinearLayout.LayoutParams(dp(44),dp(48)));
        LinearLayout heading=column();TextView title=label("Pesan Terjadwal",20);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);heading.addView(title);TextView subtitle=label("WaTrobos \u00b7 asHDEV",10);subtitle.setTextColor(secondary);subtitle.setLetterSpacing(.16f);heading.addView(subtitle);bar.addView(heading,new LinearLayout.LayoutParams(0,-2,1));bar.addView(badge("BETA",green));root.addView(bar);
        scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);body=column();body.setPadding(dp(16),dp(8),dp(16),dp(24));scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout dock=column();dock.setPadding(dp(16),dp(10),dp(16),dp(12));dock.setBackgroundColor(bg);Button add=button("\uff0b  Tambah jadwal",()->editor(null));primary(add);dock.addView(add,new LinearLayout.LayoutParams(-1,dp(52)));root.addView(dock);
        setContentView(root);root.requestApplyInsets();
    }
    @Override public void onResume(){super.onResume();resumed=true;refresh.run();loadNames();}
    @Override public void onPause(){resumed=false;handler.removeCallbacks(refresh);super.onPause();}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private GradientDrawable shape(int fill,int radius,int stroke){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(radius));if(stroke!=0)d.setStroke(dp(1),stroke);return d;}
    private TextView label(String s,int size){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(fg);v.setLineSpacing(dp(2),1);return v;}
    private TextView badge(String s,int color){TextView v=label(s,10);v.setTextColor(color);v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);v.setPadding(dp(10),dp(6),dp(10),dp(6));v.setBackground(shape((color&0x00ffffff)|0x22000000,20,0));return v;}
    private Button button(String s,Runnable action){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(14);b.setTextColor(fg);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setMinHeight(dp(44));b.setMinimumHeight(dp(44));b.setPadding(dp(12),dp(8),dp(12),dp(8));b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22ffffff),shape(0xff1c2e46,14,edge),null));b.setOnClickListener(v->action.run());return b;}
    private void primary(Button b){b.setTextColor(0xff142238);b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22ffffff),shape(green,16,0),null));}
    private void space(LinearLayout parent,int height){View gap=new View(this);parent.addView(gap,new LinearLayout.LayoutParams(1,dp(height)));}
    private LinearLayout card(){LinearLayout card=column();card.setPadding(dp(16),dp(16),dp(16),dp(16));card.setBackground(shape(surface,20,edge));return card;}
    private void addCard(LinearLayout card,int bottom){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(bottom);body.addView(card,p);}
    private void loadNames(){
        if(loadingNames)return;loadingNames=true;
        new Thread(()->{try{Map<String,String> loaded=DeviceContacts.names(this);handler.post(()->{names=loaded;rendered="";if(resumed&&!editing)render();});}catch(Exception ignored){}finally{loadingNames=false;}},"asha-schedule-names").start();
    }
    private int group(String state){if("WAITING".equals(state)||"PROCESSING".equals(state))return 1;if("QUEUED".equals(state))return 2;if("CANCELLED".equals(state))return 4;return 3;}
    private void chooseFilter(int value){filter=value;getSharedPreferences("asha_schedule_ui",0).edit().putInt("filter",filter).apply();rendered="";render();}
    private String time(long t){return new java.text.SimpleDateFormat("d MMM yyyy HH:mm:ss",new Locale("id","ID")).format(new Date(t));}
    private String status(String s){switch(s){case "WAITING":return "Terjadwal";case "PROCESSING":return "Memproses";case "QUEUED":return "Diproses WA";case "CANCELLED":return "Dibatalkan";case "FAILED":return "Gagal";case "MISSED":return "Terlewat";default:return "Perlu dicek";}}
    private void render(){
        try{
            Bundle data=call("list",null,null);
            String fingerprint=filter+"|"+data.getBoolean("enabled")+"|"+data.getString("bridge")+"|"+data.getString("jobs");
            if(editing||fingerprint.equals(rendered))return;
            int oldY=scroll.getScrollY();rendered=fingerprint;body.removeAllViews();
            JSONArray jobs=new JSONArray(data.getString("jobs","[]"));
            int[] count=new int[5];ArrayList<JSONObject> visible=new ArrayList<>();
            for(int n=0;n<jobs.length();n++){JSONObject row=jobs.getJSONObject(n);int g=group(row.optString("state"));count[0]++;count[g]++;if(filter==0||filter==g)visible.add(row);}
            Collections.sort(visible,(a,b)->{int ga=group(a.optString("state")),gb=group(b.optString("state"));if(filter==0&&ga!=gb)return Integer.compare(ga,gb);return ga==1?Long.compare(a.optLong("due"),b.optLong("due")):Long.compare(b.optLong("due"),a.optLong("due"));});
            LinearLayout hero=card();GradientDrawable gradient=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xff142238,0xff29415e});gradient.setCornerRadius(dp(22));hero.setBackground(gradient);hero.setPadding(dp(20),dp(18),dp(20),dp(18));
            LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);TextView over=label("PESAN TERENCANA",10);over.setLetterSpacing(.16f);over.setTextColor(0xffcceade);top.addView(over,new LinearLayout.LayoutParams(0,-2,1));
            Switch toggle=new Switch(this);toggle.setContentDescription("Aktifkan pesan terjadwal");toggle.setThumbTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{green,secondary}));toggle.setTrackTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{0xff77bda2,0xff536760}));toggle.setChecked(data.getBoolean("enabled"));toggle.setOnCheckedChangeListener((v,on)->{Bundle b=new Bundle();b.putBoolean("enabled",on);call("enable",null,b);render();});top.addView(toggle);hero.addView(top);space(hero,6);
            TextView heroTitle=label("Atur pesan, pilih waktunya.",24);heroTitle.setTypeface(Typeface.DEFAULT,Typeface.BOLD);heroTitle.setTextColor(Color.WHITE);hero.addView(heroTitle);space(hero,8);TextView caption=label(data.getBoolean("enabled")?"Penjadwal aktif \u2022 pesan teks sekali kirim":"Penjadwal nonaktif \u2022 jadwal disimpan",12);caption.setTextColor(0xffdcf8ec);hero.addView(caption);addCard(hero,14);
            LinearLayout stats=new LinearLayout(this);String[] statLabels={"Terjadwal","Diproses WA","Perlu dicek"};int[] statGroups={1,2,3};
            for(int i=0;i<3;i++){final int g=statGroups[i];LinearLayout stat=card();stat.setPadding(dp(12),dp(12),dp(12),dp(12));TextView value=label(Integer.toString(count[g]),25);value.setTypeface(Typeface.DEFAULT,Typeface.BOLD);value.setTextColor(g==3&&count[g]>0?0xffffc67a:green);stat.addView(value);TextView name=label(statLabels[i],11);name.setTextColor(secondary);stat.addView(name);stat.setOnClickListener(v->chooseFilter(g));stat.setContentDescription(statLabels[i]+": "+count[g]);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);if(i<2)p.rightMargin=dp(8);stats.addView(stat,p);}body.addView(stats,new LinearLayout.LayoutParams(-1,dp(82)));space(body,18);
            HorizontalScrollView filterScroll=new HorizontalScrollView(this);filterScroll.setHorizontalScrollBarEnabled(false);LinearLayout filters=new LinearLayout(this);String[] titles={"Semua","Terjadwal","Riwayat","Perlu dicek","Dibatalkan"};
            for(int i=0;i<titles.length;i++){final int selected=i;TextView chip=label(titles[i]+"  "+count[i],12);chip.setGravity(Gravity.CENTER);chip.setTypeface(Typeface.DEFAULT,Typeface.BOLD);chip.setTextColor(filter==i?0xff142238:secondary);chip.setBackground(shape(filter==i?green:surface,22,filter==i?0:edge));chip.setPadding(dp(14),dp(10),dp(14),dp(10));chip.setOnClickListener(v->chooseFilter(selected));chip.setContentDescription("Filter "+titles[i]);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,dp(44));p.rightMargin=dp(8);filters.addView(chip,p);}filterScroll.addView(filters);body.addView(filterScroll);filterScroll.post(()->filterScroll.scrollTo(Math.max(0,filters.getChildAt(filter).getLeft()-dp(32)),0));space(body,18);
            LinearLayout listHeading=new LinearLayout(this);listHeading.setGravity(Gravity.CENTER_VERTICAL);TextView section=label(filter==0?"Semua jadwal":titles[filter],18);section.setTypeface(Typeface.DEFAULT,Typeface.BOLD);listHeading.addView(section,new LinearLayout.LayoutParams(0,-2,1));TextView total=label(visible.size()+" pesan",12);total.setTextColor(secondary);listHeading.addView(total);body.addView(listHeading);space(body,12);
            if(visible.isEmpty()){LinearLayout empty=card();empty.setGravity(Gravity.CENTER);space(empty,10);TextView icon=label("\u25f7",36);icon.setTextColor(green);empty.addView(icon);space(empty,10);TextView title=label(filter==1?"Belum ada pesan terjadwal":"Belum ada pesan di sini",17);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);empty.addView(title);space(empty,8);TextView help=label(filter==1||filter==0?"Tekan Tambah jadwal untuk menyiapkan pesan pertamamu.":"Pesan dengan status ini akan tampil di sini.",13);help.setGravity(Gravity.CENTER);help.setTextColor(secondary);empty.addView(help);space(empty,14);addCard(empty,14);}
            for(JSONObject row:visible)messageCard(row);
            if(filter==0||filter==2){TextView note=label("Diproses WA: pesan sudah masuk antrean WhatsApp. Penerimaan oleh tujuan belum dipastikan.",11);note.setTextColor(secondary);body.addView(note);space(body,16);}
            LinearLayout help=card();TextView health=label(data.getString("bridge",""),12);health.setTextColor(green);help.addView(health);space(help,8);TextView guide=label("WhatsApp 2.26.43.74 / 2.26.43.76 / 2.26.44.90 / 2.26.44.92 / 2.26.46.82 \u2022 Buka WhatsApp setelah membuat jadwal dan setelah HP dimulai ulang. Jadwal lewat lebih dari 15 menit dilewati.",11);guide.setTextColor(secondary);help.addView(guide);space(help,12);help.addView(button("Buka WhatsApp / sinkronkan",()->{Intent i=getPackageManager().getLaunchIntentForPackage("com.whatsapp");if(i==null){toast("WhatsApp tidak ditemukan");return;}startActivity(i);}));addCard(help,0);
            scroll.post(()->scroll.scrollTo(0,oldY));
        }catch(Exception e){toast("Tidak dapat membaca jadwal: "+e.getClass().getSimpleName());}
    }
    private void messageCard(JSONObject row){
        String id=row.optString("id"),state=row.optString("state"),phone=row.optString("phone"),name=names.get(phone);int g=group(state);int color=g==1?green:g==3?0xffffc67a:g==2?0xff99d9c7:secondary;
        LinearLayout card=card();LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        TextView avatar=label("WA",13);avatar.setTypeface(Typeface.DEFAULT,Typeface.BOLD);avatar.setGravity(Gravity.CENTER);avatar.setTextColor(green);avatar.setBackground(shape(0xff2f3644,16,0));header.addView(avatar,new LinearLayout.LayoutParams(dp(40),dp(40)));
        LinearLayout person=column();TextView recipient=label(name==null?"+"+phone:name,15);recipient.setTypeface(Typeface.DEFAULT,Typeface.BOLD);person.addView(recipient);if(name!=null){TextView number=label("+"+phone,11);number.setTextColor(secondary);person.addView(number);}LinearLayout.LayoutParams personParams=new LinearLayout.LayoutParams(0,-2,1);personParams.leftMargin=dp(10);personParams.rightMargin=dp(8);header.addView(person,personParams);header.addView(badge(status(state),color));card.addView(header);space(card,16);
        LinearLayout schedule=new LinearLayout(this);schedule.setGravity(Gravity.CENTER_VERTICAL);ImageView clock=new ImageView(this);clock.setImageResource(android.R.drawable.ic_menu_recent_history);clock.setColorFilter(secondary);schedule.addView(clock,new LinearLayout.LayoutParams(dp(22),dp(22)));LinearLayout date=column();long due=row.optLong("due");TextView day=label(new java.text.SimpleDateFormat("EEE, d MMM yyyy",new Locale("id","ID")).format(new Date(due)),11);day.setTextColor(secondary);date.addView(day);TextView hour=label(new java.text.SimpleDateFormat("HH:mm:ss",Locale.ROOT).format(new Date(due)),22);hour.setTypeface(Typeface.DEFAULT,Typeface.BOLD);date.addView(hour);LinearLayout.LayoutParams dateParams=new LinearLayout.LayoutParams(-2,-2);dateParams.leftMargin=dp(10);schedule.addView(date,dateParams);card.addView(schedule);space(card,14);
        String message=row.optString("message");TextView bubble=label(message,15);bubble.setPadding(dp(14),dp(12),dp(14),dp(12));bubble.setBackground(shape(0xff1c2e46,14,0));bubble.setMaxLines(6);bubble.setEllipsize(android.text.TextUtils.TruncateAt.END);bubble.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Isi pesan").setMessage(message).setPositiveButton("Tutup",null).show());bubble.setContentDescription("Isi pesan. Ketuk untuk melihat lengkap. "+message);card.addView(bubble,new LinearLayout.LayoutParams(-1,-2));
        if(message.length()>200){space(card,8);TextView more=label("Ketuk pesan untuk membaca lengkap",11);more.setTextColor(secondary);card.addView(more);}
        if(g==3&&!row.optString("detail").isEmpty()){space(card,10);TextView detail=label(row.optString("detail").replace(" (termasuk pemetaan LID)",""),12);detail.setTextColor(0xffffc67a);card.addView(detail);}
        space(card,14);LinearLayout actions=new LinearLayout(this);actions.setGravity(Gravity.END);
        if("WAITING".equals(state)){Button edit=button("Ubah",()->editor(row));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(44),1);p.rightMargin=dp(8);actions.addView(edit,p);Button cancel=button("Batalkan",()->new AlertDialog.Builder(this).setTitle("Batalkan jadwal?").setMessage("Pesan ini tidak akan dikirim oleh penjadwal.").setPositiveButton("Batalkan jadwal",(d,w)->{call("cancel",id,null);render();}).setNegativeButton("Kembali",null).show());cancel.setTextColor(0xffffb4ab);actions.addView(cancel,new LinearLayout.LayoutParams(0,dp(44),1));}
        else if(!"PROCESSING".equals(state)){Button delete=button("Hapus riwayat",()->new AlertDialog.Builder(this).setTitle("Hapus riwayat ini?").setMessage("Catatan jadwal ini akan dihapus dari daftar.").setPositiveButton("Hapus",(d,w)->{call("delete",id,null);render();}).setNegativeButton("Kembali",null).show());delete.setTextColor(secondary);actions.addView(delete,new LinearLayout.LayoutParams(-2,dp(44)));}
        card.addView(actions);addCard(card,14);
    }
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private EditText field(String hint,int type){EditText e=new EditText(this);e.setHint(hint);e.setInputType(type);e.setTextColor(fg);e.setHintTextColor(secondary);e.setPadding(dp(14),dp(12),dp(14),dp(12));e.setBackground(shape(0xff1c2e46,12,edge));return e;}
    private void editor(JSONObject row){
        editing=true;
        LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);form.setPadding(dp(20),dp(8),dp(20),dp(8));
        EditText phone=field("Nomor dengan kode negara, contoh +62812\u2026",InputType.TYPE_CLASS_PHONE);
        EditText message=field("Pesan teks",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);message.setMinLines(3);
        Calendar at=Calendar.getInstance();at.setTimeInMillis(row==null?System.currentTimeMillis()+120000:row.optLong("due"));
        if(row==null)at.set(Calendar.SECOND,0);at.set(Calendar.MILLISECOND,0);
        phone.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(30)});message.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(4096)});
        if(row!=null){phone.setText(row.optString("phone"));message.setText(row.optString("message"));}
        TextView chosen=label(time(at.getTimeInMillis()),16);
        TextView recipient=label("",14);recipient.setVisibility(View.GONE);
        String[] selectedPhone={""};
        phone.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void afterTextChanged(Editable s){}
            public void onTextChanged(CharSequence s,int start,int before,int count){
                if(!s.toString().replaceAll("[+ ()-]", "").equals(selectedPhone[0]))recipient.setVisibility(View.GONE);
            }
        });
        form.addView(button("Pilih kontak / chat",()->WhatsAppContactPicker.show(this,(number,name)->{
            selectedPhone[0]=number;phone.setText(number);recipient.setText(name.isEmpty()?"Tujuan dipilih: +"+number:"Tujuan dipilih: "+name);recipient.setVisibility(View.VISIBLE);
        })));
        space(form,14);TextView to=label("TUJUAN",10);to.setTextColor(secondary);form.addView(to);space(form,6);form.addView(phone);recipient.setTextColor(green);recipient.setPadding(0,dp(6),0,0);form.addView(recipient);
        space(form,14);TextView messageTitle=label("PESAN",10);messageTitle.setTextColor(secondary);form.addView(messageTitle);space(form,6);form.addView(message);
        space(form,14);TextView when=label("WAKTU PENGIRIMAN",10);when.setTextColor(secondary);form.addView(when);space(form,6);chosen.setTextColor(green);chosen.setTypeface(Typeface.DEFAULT,Typeface.BOLD);form.addView(chosen);space(form,10);
        LinearLayout pickTime=new LinearLayout(this);Button pickDate=button("Pilih tanggal",()->new DatePickerDialog(this,(d,y,m,day)->{at.set(y,m,day);chosen.setText(time(at.getTimeInMillis()));},at.get(Calendar.YEAR),at.get(Calendar.MONTH),at.get(Calendar.DAY_OF_MONTH)).show());LinearLayout.LayoutParams dateParams=new LinearLayout.LayoutParams(0,dp(46),1);dateParams.rightMargin=dp(8);pickTime.addView(pickDate,dateParams);
        pickTime.addView(button("Pilih jam",()->new TimePickerDialog(this,(t,h,m)->{at.set(Calendar.HOUR_OF_DAY,h);at.set(Calendar.MINUTE,m);at.set(Calendar.SECOND,0);at.set(Calendar.MILLISECOND,0);chosen.setText(time(at.getTimeInMillis()));},at.get(Calendar.HOUR_OF_DAY),at.get(Calendar.MINUTE),true).show()),new LinearLayout.LayoutParams(0,dp(46),1));form.addView(pickTime);space(form,8);
        final EditText[] secondsRef={null};
        form.addView(button("1 menit dari sekarang",()->{at.setTimeInMillis(System.currentTimeMillis()+60000);if(secondsRef[0]!=null)secondsRef[0].setText(Integer.toString(at.get(Calendar.SECOND)));chosen.setText(time(at.getTimeInMillis()));}),new LinearLayout.LayoutParams(-1,dp(46)));space(form,8);
        EditText seconds=field("Detik (0\u201359)",InputType.TYPE_CLASS_NUMBER);secondsRef[0]=seconds;seconds.setText(Integer.toString(at.get(Calendar.SECOND)));seconds.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(2)});
        TextView secondsTitle=label("DETIK (0\u201359)",10);secondsTitle.setTextColor(secondary);form.addView(secondsTitle);space(form,6);form.addView(seconds);space(form,8);
        ScrollView scroll=new ScrollView(this);scroll.addView(form);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(row==null?"Tambah jadwal":"Ubah jadwal").setView(scroll).setPositiveButton("Simpan jadwal",null).setNegativeButton("Batal",null).create();
        dialog.setOnDismissListener(d->{editing=false;render();});
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            try{int sec=Integer.parseInt(seconds.getText().toString());if(sec<0||sec>59)throw new IllegalArgumentException("Detik harus 0 sampai 59.");at.set(Calendar.SECOND,sec);Bundle b=new Bundle();b.putString("phone",phone.getText().toString());b.putString("message",message.getText().toString());b.putLong("due",at.getTimeInMillis());if(row!=null)b.putString("id",row.optString("id"));call("save",null,b);filter=1;getSharedPreferences("asha_schedule_ui",0).edit().putInt("filter",filter).apply();dialog.dismiss();render();toast("Jadwal disimpan. Pastikan fitur aktif dan buka WhatsApp untuk sinkronisasi.");}catch(Exception e){toast(e.getMessage()==null?"Tidak dapat menyimpan jadwal":e.getMessage());}
        }));dialog.show();
    }
}
