package com.wmods.wppenhacer.scheduler;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import org.json.*;
import java.util.*;

/** Actual Android address book, with the user's saved display names. */
final class DeviceContacts {
    static boolean permitted(Context c){return c.checkSelfPermission(Manifest.permission.READ_CONTACTS)==PackageManager.PERMISSION_GRANTED;}
    private static String region(Context c){
        String region="";
        try{region=((TelephonyManager)c.getSystemService(Context.TELEPHONY_SERVICE)).getNetworkCountryIso();}catch(RuntimeException ignored){}
        if(region==null||region.isEmpty())region=Locale.getDefault().getCountry();
        return region==null||region.isEmpty()?"ID":region.toUpperCase(Locale.ROOT);
    }
    private static String number(String raw,String normalized,String region){
        String value=normalized==null||normalized.isEmpty()?raw:normalized;
        String e164=PhoneNumberUtils.formatNumberToE164(value,region);
        if(e164==null)return null;String phone=e164.replace("+","");
        return phone.matches("[1-9][0-9]{6,14}")?phone:null;
    }
    static boolean matches(String name,String phone,String raw,String query){
        String q=query.trim().toLowerCase(Locale.ROOT);
        if(q.isEmpty()||name.toLowerCase(Locale.ROOT).contains(q))return true;
        String digits=q.replaceAll("[+ ()-]","");
        if(!digits.matches("[0-9]+"))return false;
        if(phone.contains(digits)||raw.replaceAll("[^0-9]","").contains(digits))return true;
        return digits.startsWith("0")&&digits.length()>1&&phone.contains(digits.substring(1));
    }
    static JSONArray search(Context c,String query,int limit)throws JSONException {
        if(!permitted(c))throw new SecurityException("Contacts permission needed");
        JSONArray rows=new JSONArray();HashSet<String> seen=new HashSet<>();String region=region(c);
        String[] columns={Phone.DISPLAY_NAME,Phone.NUMBER,Phone.NORMALIZED_NUMBER};
        try(Cursor cursor=c.getContentResolver().query(Phone.CONTENT_URI,columns,null,null,Phone.DISPLAY_NAME+" COLLATE LOCALIZED ASC")) {
            if(cursor==null)return rows;
            while(cursor.moveToNext()&&rows.length()<limit){
                String name=cursor.getString(0),raw=cursor.getString(1);if(name==null||name.trim().isEmpty()||raw==null)continue;
                name=name.trim().replaceAll("[\\p{Cntrl}]"," ");String phone=number(raw,cursor.getString(2),region);
                if(phone==null||seen.contains(phone)||!matches(name,phone,raw,query))continue;
                seen.add(phone);JSONObject row=new JSONObject();row.put("phone",phone);row.put("name",name);row.put("saved",true);rows.put(row);
            }
        }return rows;
    }
    static java.util.Map<String,String> names(Context c)throws JSONException {
        java.util.Map<String,String> result=new java.util.HashMap<>();if(!permitted(c))return result;
        JSONArray rows=search(c,"",20000);for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);result.put(row.getString("phone"),row.getString("name"));}return result;
    }
}
