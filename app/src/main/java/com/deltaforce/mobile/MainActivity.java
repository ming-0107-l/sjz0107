package com.deltaforce.mobile;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.*;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    EditText baseUrl, jsonInput; TextView status, result; ImageView qr;
    ExecutorService pool = Executors.newSingleThreadExecutor();
    SharedPreferences prefs;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b); setContentView(R.layout.activity_main);
        baseUrl=findViewById(R.id.baseUrl); jsonInput=findViewById(R.id.jsonInput);
        status=findViewById(R.id.status); result=findViewById(R.id.result); qr=findViewById(R.id.qr);
        prefs=getSharedPreferences("settings",0); baseUrl.setText(prefs.getString("baseUrl",""));
        findViewById(R.id.saveUrl).setOnClickListener(v -> saveUrl());
        findViewById(R.id.qq).setOnClickListener(v -> startQQ("qq"));
        findViewById(R.id.wechat).setOnClickListener(v -> startWechat("wechat"));
        findViewById(R.id.wegame).setOnClickListener(v -> showWeGame());
        findViewById(R.id.pioneer).setOnClickListener(v -> showPioneer());
        findViewById(R.id.qqsafe).setOnClickListener(v -> showQQSafe());
        findViewById(R.id.test).setOnClickListener(v -> call("qq/sig", null, true));
    }
    void saveUrl(){String u=baseUrl.getText().toString().trim();if(u.isEmpty()){status.setText("请先填写 API 地址");return;}if(!u.endsWith("/"))u+="/";prefs.edit().putString("baseUrl",u).apply();baseUrl.setText(u);status.setText("API 地址已保存");}
    String url(String p){String u=baseUrl.getText().toString().trim();if(!u.endsWith("/"))u+="/";return u+p;}
    JSONObject dataOf(JSONObject j){JSONObject d=j.optJSONObject("data");return d==null?new JSONObject():d;}
    void startQQ(String type){
        saveUrl(); status.setText("正在获取二维码…"); qr.setVisibility(android.view.View.GONE);
        callJson("GET",type+"/sig",null,j->{try{if(j.optInt("code",-1)!=0){fail(j.optString("msg","获取失败"));return;}JSONObject d=dataOf(j);showImage(d.optString("image"));pollQQ(type,d.optString("token"),d.optString("qrSig"),d.optString("loginSig"),d.optJSONObject("cookie").toString());}catch(Exception e){fail(e.getMessage());}},()->{});
    }
    void pollQQ(String type,String token,String sig,String loginSig,String cookie){
        status.setText("请使用手机扫码…"); final int[] n={0};
        Runnable r=new Runnable(){public void run(){if(n[0]++>120){fail("二维码轮询超时");return;}
            JSONObject p=new JSONObject();try{p.put("qrToken",token);p.put("qrSig",sig);p.put("loginSig",loginSig);p.put("cookie",cookie);}catch(Exception ignored){}
            callJson("POST",type+"/status",p,j->{int code=j.optInt("code",-99);if(code==0){status.setText("登录成功");result.setText(pretty(j));}else if(code==1||code==2){status.setText(j.optString("msg","等待扫码…"));new Handler(Looper.getMainLooper()).postDelayed(this,1500);}else fail(j.optString("msg","登录失败"));},()->new Handler(Looper.getMainLooper()).postDelayed(this,1500));
        }};new Handler(Looper.getMainLooper()).post(r);
    }
    void startWechat(String type){
        saveUrl();qr.setVisibility(android.view.View.GONE);status.setText("正在获取微信二维码…");
        callJson("GET",type+"/login",null,j->{if(j.optInt("code",-1)!=0){fail(j.optString("msg","获取失败"));return;}JSONObject d=dataOf(j);String q=d.optString("qrCode");if(q.isEmpty()){fail("二维码地址为空");return;}loadRemoteImage(q);pollWechat(type,d.optString("uuid"));},()->{});
    }
    void pollWechat(String type,String uuid){
        final int[] n={0};Runnable r=new Runnable(){public void run(){if(n[0]++>120){fail("二维码轮询超时");return;}
            JSONObject p=new JSONObject();try{p.put("uuid",uuid);}catch(Exception ignored){}
            callJson("POST",type+"/status",p,j->{int code=j.optInt("code",-99);status.setText(j.optString("msg",""));if(code==1||code==2){new Handler(Looper.getMainLooper()).postDelayed(this,1500);}else if(code==3){result.setText(pretty(j));status.setText("微信登录成功");}else fail(j.optString("msg","登录失败"));},()->new Handler(Looper.getMainLooper()).postDelayed(this,1500));
        }};new Handler(Looper.getMainLooper()).post(r);
    }
    void showWeGame(){new AlertDialog.Builder(this).setTitle("WeGame").setItems(new String[]{"QQ扫码登录","微信扫码登录","领取礼包","抽卡/卡牌"},(d,w)->{if(w==0)startQQ("wegame/qq");else if(w==1)startWechat("wegame/wechat");else if(w==2)call("wegame/gift",readJson(),true);else call("wegame/card",readJson(),true);}).show();}
    void showPioneer(){new AlertDialog.Builder(this).setTitle("Pioneer").setItems(new String[]{"QQ扫码登录","游戏测试列表"},(d,w)->{if(w==0)startQQ("pioneer/qq");else call("pioneer/list",readJson(),true);}).show();}
    void showQQSafe(){new AlertDialog.Builder(this).setTitle("QQSafe").setItems(new String[]{"QQSafe扫码登录","封禁列表","查询/上报"},(d,w)->{if(w==0)startQQ("qqsafe");else if(w==1)call("qqsafe/bannedList",readJson(),true);else call("qqsafe/report",readJson(),true);}).show();}
    JSONObject readJson(){try{return new JSONObject(jsonInput.getText().toString().trim().isEmpty()?"{}":jsonInput.getText().toString().trim());}catch(Exception e){fail("JSON 格式错误");return null;}}
    void call(String path,JSONObject p,boolean show){saveUrl();callJson(p==null?"GET":"POST",path,p,j->{if(show){status.setText(j.optString("msg","请求完成"));result.setText(pretty(j));}},()->fail("网络请求失败"));}
    void callJson(String method,String path,JSONObject p,java.util.function.Consumer<JSONObject> ok,Runnable retry){
        pool.execute(()->{try{
            HttpURLConnection c=(HttpURLConnection)new URL(url(path)).openConnection();c.setRequestMethod(method);c.setConnectTimeout(12000);c.setReadTimeout(20000);c.setRequestProperty("Accept","application/json");
            if(p!=null){c.setDoOutput(true);c.setRequestProperty("Content-Type","application/x-www-form-urlencoded; charset=UTF-8");StringBuilder form=new StringBuilder();java.util.Iterator<String> it=p.keys();while(it.hasNext()){String k=it.next();if(form.length()>0)form.append('&');form.append(URLEncoder.encode(k,"UTF-8")).append('=').append(URLEncoder.encode(p.optString(k), "UTF-8"));}try(OutputStream o=c.getOutputStream()){o.write(form.toString().getBytes(StandardCharsets.UTF_8));}}
            int http=c.getResponseCode();InputStream in=http>=400?c.getErrorStream():c.getInputStream();String s=read(in);JSONObject j=new JSONObject(s);runOnUiThread(()->ok.accept(j));
        }catch(Exception e){runOnUiThread(retry);}});
    }
    String read(InputStream in)throws Exception{if(in==null)return "{}";BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8));StringBuilder s=new StringBuilder();String x;while((x=r.readLine())!=null)s.append(x);return s.toString();}
    void showImage(String b64){try{byte[] b=Base64.decode(b64,Base64.DEFAULT);Bitmap m=BitmapFactory.decodeByteArray(b,0,b.length);runOnUiThread(()->{qr.setImageBitmap(m);qr.setVisibility(android.view.View.VISIBLE);});}catch(Exception e){fail("二维码图片解析失败");}}
    void loadRemoteImage(String u){pool.execute(()->{try{HttpURLConnection c=(HttpURLConnection)new URL(u).openConnection();c.setConnectTimeout(12000);c.setReadTimeout(20000);Bitmap b=BitmapFactory.decodeStream(c.getInputStream());runOnUiThread(()->{qr.setImageBitmap(b);qr.setVisibility(android.view.View.VISIBLE);});}catch(Exception e){fail("微信二维码加载失败");}});}
    String pretty(JSONObject j){try{return j.toString(2);}catch(Exception e){return j.toString();}}
    void fail(String s){runOnUiThread(()->status.setText("提示："+s));}
    @Override protected void onDestroy(){pool.shutdownNow();super.onDestroy();}
}
