package com.taobaoke.android.activity;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.taobaoke.android.BuildConfig;
import com.taobaoke.android.R;
import com.taobaoke.android.application.AppMain;
import com.taobaoke.android.application.MainApplication;
import com.taobaoke.android.client.BaseApiCallback;
import com.taobaoke.android.client.ClientApis;
import com.taobaoke.android.constants.UrlConstants;
import com.taobaoke.android.entity.PageItem;
import com.taobaoke.android.entity.SsData;
import com.taobaoke.android.social.SocialApi;
import com.taobaoke.android.social.SocialBaseData;
import com.taobaoke.android.social.SocialBean;
import com.taobaoke.android.social.SocialConstants;
import com.taobaoke.android.social.SocialListener;
import com.taobaoke.android.social.SocialUtils;
import com.taobaoke.android.store.DebugData;
import com.taobaoke.android.store.SettingData;
import com.taobaoke.android.view.ShareBoardDlg;
import com.umeng.analytics.AnalyticsConfig;
import com.umeng.analytics.MobclickAgent;
import com.yjoy800.tools.Logger;
import com.yjoy800.tools.ManifestUtils;

import java.io.File;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import cn.finalteam.toolsfinal.io.FileUtils;


public class AboutActivity extends BaseActivity {

    private static Logger log = Logger.getLogger(AboutActivity.class.getSimpleName());

    @BindView(R.id.tv_version)
    TextView tvVersion;
    @BindView(R.id.tv_channel)
    TextView tvChannel;

    @BindView(R.id.tv_titlebar_text)
    TextView tvTitlebarText;

    @BindView(R.id.tv_debug)
    TextView tvDebug;
    @BindView(R.id.btn_debugchangeserver)
    Button btnDebugchangeserver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);
        ButterKnife.bind(this);

        initTitleBar();
        initData();
    }

    private void initTitleBar() {
        tvTitlebarText.setText("关于我们");
    }

    private void initData() {
        if (BuildConfig.DEBUG) {
            tvDebug.setVisibility(View.VISIBLE);
            tvDebug.setText("只有测试版会出现此内容\n" + UrlConstants.BASE_URL);

            btnDebugchangeserver.setVisibility(View.VISIBLE);
        }

        tvVersion.setText("版本号：" + ManifestUtils.getVersionName(getApplicationContext()));

        //String channel = AnalyticsConfig.getChannel(mAppContext);
        //String channel = BuildConfig.FLAVOR.substring(8);
        String channel = MainApplication.getChannel();
        tvChannel.setText("渠道: [" + channel + "]");
    }

    @OnClick({R.id.iv_titlebar_back, R.id.iv_logo,R.id.center_qqgroup_panel,  R.id.btn_debugchangeserver})
    public void onViewClicked(View view) {
        switch (view.getId()) {
            case R.id.iv_titlebar_back:
                finish();
                break;
            case R.id.iv_logo:
                clickLogo();
                break;
            case R.id.center_qqgroup_panel:
                MobclickAgent.onEvent(mAppContext, "center_qqgroup");
                addQQGroup();
                break;
            case R.id.btn_debugchangeserver:
                debugChangeServer();
                break;
        }
    }

    @OnClick({R.id.center_service_panel})
    public void onButtonWebPageClicked(View view) {
        PageItem p = SettingData.readStaticPages(mAppContext);
        if (p == null) {
            return;
        }

        String pageUrl = null;
        switch (view.getId()) {
            case R.id.center_service_panel:
                pageUrl = p.getKfzx();
                break;
        }
        if (!TextUtils.isEmpty(pageUrl)) {
            String url = UrlConstants.getUrl(pageUrl);
            AppMain.openNormalPage(this, url, null);
        } else {
            showToast("网络错误，请重试！");
        }

    }

    /**
     *  两秒之内，连续点击logo图标8次，显示渠道信息
     */
    private int mClickCount;
    private long mFirstClickTime;

    private void clickLogo(){
        if(mClickCount == 0){
            mFirstClickTime = System.currentTimeMillis();
        }

        mClickCount++;

        long t = (System.currentTimeMillis() - mFirstClickTime);

        if(Logger.DEBUG){
            log.i("Click count:" + mClickCount + ", time:" + t);
        }

        if(t > 2000){
            mClickCount = 0;
            mFirstClickTime = 0;
        }else{
            if(mClickCount >= 8){
                tvChannel.setVisibility(View.VISIBLE);
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (Logger.DEBUG) {
            log.w("onPause");
        }
        dismissProgressDialog();
    }

    private void addQQGroup() {
        String key = SettingData.getQQGroup(mAppContext);
        if (TextUtils.isEmpty(key)) {
            key = "nCuTWalnbCKn4j-zaTkPv1QOiQGw_xX1";
        }
        SocialUtils.joinQQGroup(this, key);
    }
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (mSocialApi != null) {
            mSocialApi.doResultIntent(intent);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (mSocialApi != null) {
            mSocialApi.onActivityResult(requestCode, resultCode, data);
        }
    }

    /**
     * 分享功能相关操作
     */
    private SocialApi mSocialApi;

    private void releaseSocial() {
        if (mSocialApi != null) {
            mSocialApi.destroy();
            mSocialApi = null;
        }
    }

    private void clickShare() {
        MobclickAgent.onEvent(mAppContext, "center_share");

        ShareBoardDlg.show(this, "1,2,3,4,5", new ShareBoardDlg.OnShareDialogClickListener() {
            @Override
            public void onClick(int platform) {
                showProgressDialog();
                requestShareInfo(platform);
            }
        });
    }

    private void requestShareInfo(final int platform) {
        ClientApis.apiGetShareInfo(new BaseApiCallback<SsData>() {
            @Override
            public void onFailure(int code, String msg) {
                dismissProgressDialog();
                showToast("分享信息无效，请重试！");
            }

            @Override
            public void onSuccess(SsData data, String msg) {
                doShareMedia(platform, data);
            }
        });
    }

    private void doShareMedia(int platform, SsData data) {
        releaseSocial();

        mSocialApi = SocialApi.get(this, platform);
        if ((platform == SocialConstants.PLATFORM_WEIXIN || platform == SocialConstants.PLATFORM_WEIXIN_MOMENT)
                && !mSocialApi.isAppInstalled()) {//其他平台不需要提示
            Toast.makeText(this, "请先安装微信客户端", Toast.LENGTH_SHORT).show();
            dismissProgressDialog();
            releaseSocial();
            return;
        }

        SocialBean bean = new SocialBean();
        bean.setTitle(data.getTitle());
        bean.setDescription(data.getDesp());
        bean.setIconUrl(data.getIcon());
        String url = UrlConstants.BASE_URL + data.getUrl();
        bean.setPageUrl(url);
        bean.setType(SocialConstants.TYPE_PAGE);

        mSocialApi.doShareMedia(platform, bean,
                new SocialListener() {
                    @Override
                    public void onComplete(SocialBaseData result) {
                        Toast.makeText(MainApplication.getContext(), "分享成功，谢谢！", Toast.LENGTH_SHORT).show();
                        dismissProgressDialog();
                        releaseSocial();
                    }

                    @Override
                    public void onCancel() {
                        dismissProgressDialog();
                        releaseSocial();
                    }

                    @Override
                    public void onError(String msg) {
                        Toast.makeText(MainApplication.getContext(), "分享失败", Toast.LENGTH_SHORT).show();
                        dismissProgressDialog();
                        releaseSocial();
                    }
                });

    }



    String[] servers = new String[]{
            "http://192.168.31.30/htmmall",
            "http://192.168.31.134/htmmall",
            "http://192.168.31.173:8080/htmmall",
            "http://192.168.31.51:8080/htmmall",
            "http://192.168.31.99/htmmall",
            "http://test1.duobaobuluo.com/htmmall",
            "http://api.duobaobuluo.com/htmmall",
    };

    private void debugChangeServer(){
        AlertDialog dlg = new AlertDialog.Builder(this)
                .setItems(servers, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        changeServer(which);
                    }
                }).create();
        dlg.show();
    }

    private void changeServer(int index){
        String dir = mAppContext.getFilesDir().getAbsolutePath();
        int lastPos = dir.lastIndexOf('/');
        String dataDir = dir.substring(0, lastPos);
        if(Logger.DEBUG){
            log.i("data dir :" + dataDir);
        }
        FileUtils.deleteQuietly(new File(dataDir));


        String baseUrl = servers[index];
        DebugData.setBaseUrl(mAppContext, baseUrl);
        if(Logger.DEBUG){
            log.i("baseUrl :" + baseUrl);
        }

        Toast.makeText(this, "请杀掉程序，重新启动！", Toast.LENGTH_LONG).show();
    }
}
