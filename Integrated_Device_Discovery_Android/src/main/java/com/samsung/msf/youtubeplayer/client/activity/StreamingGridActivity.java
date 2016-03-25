package com.samsung.msf.youtubeplayer.client.activity;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.LoaderManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Loader;
import android.graphics.drawable.AnimationDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.media.MediaRouteSelector;
import android.support.v7.media.MediaRouter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.SimpleAdapter;
import android.widget.Toast;

import com.google.android.gms.cast.Cast;
import com.google.android.gms.cast.CastDevice;
import com.google.android.gms.cast.CastMediaControlIntent;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.RemoteMediaPlayer;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.nostra13.universalimageloader.cache.disc.naming.Md5FileNameGenerator;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.ImageLoaderConfiguration;
import com.nostra13.universalimageloader.core.assist.QueueProcessingType;
import com.samsung.msf.youtubeplayer.DownloadTaskLoader;
import com.samsung.msf.youtubeplayer.client.util.FeedParser;
import com.samsung.msf.youtubeplayer.client.util.TestUtil;
import com.samsung.multiscreen.*;
import com.samsung.multiscreen.Error;
import com.samsung.msf.youtubeplayer.client.adapter.StreamingGridViewAdapter;
import com.samsung.msf.youtubeplayer.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;



public class StreamingGridActivity extends Activity implements LoaderManager.LoaderCallbacks<List<FeedParser.Entry>> {
    private Context mContext;
    private GridView mGridView;
    private StreamingGridViewAdapter mGridViewAdapter;
    private String TAG = "StreamingGridActivity";

    private static final String CONNECT_DEVICE = "connect";
    private static final String DISCONNECT_DEVICE = "disconnect";
    private static final String CONNECTING_DEVICE = "connecting";

    private static final String SMART_VIEW_TYPE = "SMART_VIEW_SDK";
    private static final String CHROME_CAST_TYPE = "CHROME_CAST";




    /**
     * Options menu used to populate ActionBar.
     */
    private Menu mOptionsMenu;
    private String mConnectSatus;
    AnimationDrawable  mMenuCast;

    /**
     * for MSF Lib
     */
    private Search mSearch;
    private Service mService;
    private Application mApplication;
    private ArrayList mDeviceList = new ArrayList();
//    private ArrayAdapter mTVListAdapter;
    private SimpleAdapter mTVListAdapter;
    private ArrayList<String> mDeviceNames = new ArrayList<String>();
    private List<Map<String,String>> mDeviceInfos = new ArrayList<Map<String,String>>();

    /**
     * For intergrate Google Cast Device discover
     */
    private String mConnectionDeviceType;
    private String mSessionId;
    private MediaRouter mMediaRouter;
    private MediaRouteSelector mMediaRouteSelector;
    private MediaRouter.Callback mMediaRouterCallback;
    private CastDevice mCastSelectedDevice;
    private GoogleApiClient mApiClient;
    private GoogleApiClient.ConnectionCallbacks mConnectionCallbacks;
    private Cast.Listener mCastListener;
    private ConnectionFailedListener mConnectionFailedListener;
    private RemoteMediaPlayer mRemoteMediaPlayer = new RemoteMediaPlayer();


    /**
     * reserved tv webapp. see tizen config.xml
     */
    private String mApplicationId = "0rLFmRVi9d.youtubetest";
    private String mChannelId = "com.samsung.msf.youtubetest";


    private boolean mFullScreen;

    @Override
    public Loader<List<FeedParser.Entry>> onCreateLoader(int id, Bundle args) {
        Log.d(TAG, "onCreateLoader ");
        DownloadTaskLoader loader = new DownloadTaskLoader(this);
        if(!loader.isAPIKeyEnable()){
            Toast.makeText(mContext, "Set Your API KEY ", Toast.LENGTH_LONG).show();
            loader.stopLoading();
            return null;
        }

        if(!isNetworkEnable()){
            Toast.makeText(mContext,"Check Your Network.",Toast.LENGTH_LONG).show();
            loader.stopLoading();
            return null;
        }
        loader.forceLoad();
        return loader;
    }

    @Override
    public void onLoadFinished(Loader<List<FeedParser.Entry>> loader, List<FeedParser.Entry> data) {
        Log.d(TAG, "onLoadFinished ");
        for (FeedParser.Entry e : data) {
            mGridViewAdapter.addItem(e);
        }
        mGridViewAdapter.notifyDataSetChanged();
        //setRefreshActionButtonState(false);

    }

    @Override
    public void onLoaderReset(Loader<List<FeedParser.Entry>> loader) {
        Log.d(TAG, "onLoaderReset ");
    }

    public boolean onQueryTextChanged(String newText) {
        // Called when the action bar search text has changed.  Update
        // the search filter, and restart the loader to do a new query
        // with this filter.

        if ("".equals(newText))
            return false;
        else {
            FeedParser.setmSearchData(newText);
            mGridViewAdapter.clear();
            getLoaderManager().restartLoader(0, null, this);
            return true;
        }
    }

    private boolean isNetworkEnable(){
        // Get ConnectivityManager
        ConnectivityManager cm =
                (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);

        // Get infomation mobile network
        NetworkInfo ni = cm.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
        boolean isMobileConn = ni.isConnected();

        // Get infomation wifi network
        ni = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        boolean isWifiConn = ni.isConnected();

        if(isWifiConn || isMobileConn) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate ");
        mContext = this.getBaseContext();
        setContentView(R.layout.main_activity);


        setConnectionStatus(DISCONNECT_DEVICE);

        getActionBar().setDisplayShowHomeEnabled(false);

        mMenuCast = (AnimationDrawable)getResources().getDrawable(R.drawable.menu_cast);

        mGridView = (GridView) findViewById(R.id.main_grid_view);
        mGridViewAdapter = new StreamingGridViewAdapter(this);
        mGridView.setAdapter(mGridViewAdapter);

        //For intergrate Google Cast Device discover
        // Configure Cast device discovery
        mMediaRouter = MediaRouter.getInstance(getApplicationContext());
        mMediaRouteSelector = new MediaRouteSelector.Builder()
                .addControlCategory(CastMediaControlIntent.categoryForCast(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)).build();
        mMediaRouterCallback = new MyMediaRouterCallback();


        // Prepare the loader.  Either re-connect with an existing one,
        // or start a new one.
        getLoaderManager().initLoader(0, null, this);

        // AUIL - Set ImageLoader config
        initImageLoader(mContext);

//        mTVListAdapter = new ArrayAdapter<String>(
//                this,
//                android.R.layout.select_dialog_item,mDeviceNames);

        mTVListAdapter = new SimpleAdapter(
                this,
                mDeviceInfos,
                android.R.layout.simple_list_item_2,
                new String[]{"name","type"},
                new int[]{android.R.id.text1,android.R.id.text2 });


        mGridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
                if (mGridViewAdapter != null) {
                    Log.d(TAG, "touched position is " + position);
                    FeedParser.Entry streamingItemInfo = (FeedParser.Entry) mGridViewAdapter.getItem(position);
                    Log.d(TAG, "id is " + streamingItemInfo.id + ", installed is " + streamingItemInfo.installed);
                    mFullScreen = false;
                    //if run search device and choose device.
                    //item starts selected TV
                    //
                    //Change logic to Integrate.
                    //1. Not Connect --> show Toast to connect
                    //2. samsung tv connect --> youtube sample app launch
                    //3. chromeCast connect --> DMP launch

                    if(DISCONNECT_DEVICE.equals(mConnectSatus)){
                        Toast.makeText(mContext, "Make connection first.", Toast.LENGTH_LONG).show();
                    }else if(CONNECT_DEVICE.equals(mConnectSatus)){
                        String connectionType =getConnectDeviceType();
                        if (Service.class.getName().equals(connectionType)) {
                            String event = "play";
                            JSONObject messageData = new JSONObject();
                            try {
                                messageData.put("videoId", streamingItemInfo.id);
                                messageData.put("videoName", streamingItemInfo.title);
                                messageData.put("videoThumnail", streamingItemInfo.thumnail);
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }

                            Log.d(TAG, "application.publish: " + "messageData" + messageData.toString());
                            mApplication.publish(event, messageData.toString());

                            Log.d(TAG, "application.publish: " + "VideoId" + streamingItemInfo.id);

    //                            mApplication.publish(event, "https://www.youtube.com/embed/" + streamingItemInfo.streamingVideoId);
    //                            Log.d(TAG, "application.publish: " + "https://www.youtube.com/embed/UCOpcACMWblDls9Z6GERVi1A" + streamingItemInfo.streamingVideoId);

    //                            mApplication.publish(event, streamingItemInfo.streamingImageUrl);
    //                            Log.d(TAG, "application.publish: " + streamingItemInfo.streamingImageUrl);
                        } else if (CastDevice.class.getName().equals(connectionType)) {
                            MediaMetadata mediaMetadata = new MediaMetadata(MediaMetadata.MEDIA_TYPE_GENERIC);
                            String contenUrl = "http://www.youtube.com/embed/" + streamingItemInfo.id+"?enablejsapi=1&autoplay=1";
                            Log.d(TAG, "mediaInfo "+contenUrl);
                            mediaMetadata.putString(MediaMetadata.KEY_TITLE, streamingItemInfo.title);
                            //"https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
                            //"https://ia700406.us.archive.org/31/items/ElephantsDream/ed_hd_512kb.mp4
                            //"https://download.blender.org/durian/movies/Sintel.2010.720p.mkv
                            //"http://ftp.halifax.rwth-aachen.de/blender/demo/movies/ToS/tears_of_steel_1080p.mov
                            MediaInfo mediaInfo = new MediaInfo.Builder(
                                    "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4")
                                    .setContentType("video/mp4")
                                    .setStreamType(MediaInfo.STREAM_TYPE_NONE)
                                    .setMetadata(mediaMetadata)
                                    .build();
                            try {
                                mRemoteMediaPlayer.load(mApiClient, mediaInfo, true)
                                        .setResultCallback(new ResultCallback<RemoteMediaPlayer.MediaChannelResult>() {
                                            @Override
                                            public void onResult(RemoteMediaPlayer.MediaChannelResult result) {
                                                Log.d(TAG, "Media loaded onResult "+result.getStatus());
                                                if (result.getStatus().isSuccess()) {
                                                    Log.d(TAG, "Media loaded successfully");
                                                }
                                            }
                                        });
                            } catch (IllegalStateException e) {
                                Log.e(TAG, "Problem occurred with media during loading", e);
                            } catch (Exception e) {
                                Log.e(TAG, "Problem opening media during loading", e);
                            }
                        }

                    }

                    //Change logic to Intergrate __OLD VERSION
//                    if (mService != null) {
//                        //MSF
//                        //1.createApplication
//                        //  1.1 need to uri , channelId
//                        //2.launch : application.connect
//
//
//                        if (mApplication == null) {
//
//                            mApplication = mService.createApplication(mApplicationId, mChannelId);
//                            Log.d(TAG, "mApplication 1 : " + mApplication);
//                            Log.d(TAG, "createApplications mApplicationId : " + mApplicationId + "mChannelId : " + mChannelId);
//                            mApplication.connect(new Result<Client>() {
//                                @Override
//                                public void onSuccess(Client client) {
//                                    mApplication.setDebug(true);
//                                    setRefreshActionButtonState(CONNECT_DEVICE);
//                                    Log.d(TAG, "application.connect onSuccess " + client.toString());
//                                }
//
//                                @Override
//                                public void onError(com.samsung.multiscreen.Error error) {
//                                    Log.d(TAG, "application.connect onError " + error.toString());
//
//                                }
//                            });
//                        }
//
//                        String event = "play";
////                        String messageData = "Hello world";
//                        JSONObject messageData = new JSONObject();
//                        try {
//                            messageData.put("videoId", streamingItemInfo.id);
//                            messageData.put("videoName", streamingItemInfo.title);
//                            messageData.put("videoThumnail", streamingItemInfo.thumnail);
//                        } catch (JSONException e) {
//                            e.printStackTrace();
//                        }
//
//                        Log.d(TAG, "application.publish: " + "messageData" + messageData.toString());
//                        mApplication.publish(event, messageData.toString());
//
//                        Log.d(TAG, "application.publish: " + "VideoId" + streamingItemInfo.id);
//
////                            mApplication.publish(event, "https://www.youtube.com/embed/" + streamingItemInfo.streamingVideoId);
////                            Log.d(TAG, "application.publish: " + "https://www.youtube.com/embed/UCOpcACMWblDls9Z6GERVi1A" + streamingItemInfo.streamingVideoId);
//
////                            mApplication.publish(event, streamingItemInfo.streamingImageUrl);
////                            Log.d(TAG, "application.publish: " + streamingItemInfo.streamingImageUrl);
//                    } else {
//                        // Get a URI for the selected item, then start an Activity that displays the URI. Any
//                        // Activity that filters for ACTION_VIEW and a URI can accept this. In most cases, this will
//                        // be a browser.
//
//                        //New Youtube API3.0
//                        // + videoId
//                        String YoutubeVideo = "http://www.youtube.com/watch?v=" + streamingItemInfo.id;
//                        Log.i(TAG, "Opening URL: " + YoutubeVideo);
//                        // Get a Uri object for the URL string
//                        Uri articleURL = Uri.parse(YoutubeVideo);
//                        Intent i = new Intent(Intent.ACTION_VIEW, articleURL);
//                        startActivity(i);
//
//                    }
                }
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume ");

    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG, "onPause ");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy ");

        if (mSearch != null) {
            mSearch.stop();
            mSearch = null;
        }

        // End media router discovery
        mMediaRouter.removeCallback(mMediaRouterCallback);

        disconnectTV(true, true);
        teardown(true);
    }

    /**
     * Create the ActionBar.
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        mOptionsMenu = menu;
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    /**
     * Respond to user gestures on the ActionBar.
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            //1. disconnect  TV
            case R.id.menu_disconnect:
                disconnectTV(true, false);
                teardown(false);
                return true;

            //1. MSF TV Search
            case R.id.menu_search_device:

                if(DISCONNECT_DEVICE.equals(mConnectSatus)){
                    //Start Discover and show device list
                    CreateSearchTvDialog();
                }else if(CONNECT_DEVICE.equals(mConnectSatus)){
                    //Show control and disconnet button
                    CreateDisconnectTvDialogDialog();
                }

                return true;

            // If the user clicks the "Refresh" button.
            case R.id.menu_refresh:
                getLoaderManager().restartLoader(0, null, this);
                //setRefreshActionButtonState(true);
                return true;

            case R.id.menu_control:
                if (mApplication == null) {
                    Toast.makeText(mContext, "Make connection first.", Toast.LENGTH_LONG).show();
                } else {
                    CreateControlDialog();
                }

                return true;

            // . Youtube data  Search
            case R.id.menu_search_data:
                CreateSearchDataDialog();
                return true;

            // . TEST API
            case R.id.menu_start:
                TestUtil.startApplication();

                return true;
            // . TEST API
            case R.id.menu_stop:
                TestUtil.stopApplication();

                return true;
            // . TEST API
            case R.id.menu_getinfo:
                TestUtil.getInfoApplication();
                return true;
            // . TEST API
            case R.id.menu_install:
                TestUtil.installApplication();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void setRefreshActionButtonState(String  state) {
        if (mOptionsMenu == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
            return;
        }

        final MenuItem refreshItem = mOptionsMenu.findItem(R.id.menu_search_device);
        setConnectionStatus(state);
        refreshItem.setIcon(R.drawable.menu_cast);
        AnimationDrawable icon = (AnimationDrawable)refreshItem.getIcon();
        if (refreshItem != null) {
            if (CONNECT_DEVICE.equals(state)) {
                icon.stop();
                refreshItem.setIcon(R.drawable.ic_cast_connected_white_24dp);
            } else if(DISCONNECT_DEVICE.equals(state)){
                icon.stop();
                refreshItem.setIcon(R.drawable.ic_cast_white_24dp);
            } else if(CONNECTING_DEVICE.equals(state)){
                //TO DO : animate actionbar icon
                icon.start();
            }
        }
    }

    private void CreateSearchTvDialog() {
        AlertDialog.Builder alertBuilder = new AlertDialog.Builder(this);
        alertBuilder.setIcon(R.drawable.ic_cast_white_24dp);
        alertBuilder.setTitle(R.string.connect_device);

        //MSF Search
        mSearch = Service.search(mContext);
        mSearch.start();

        // Start media router discovery
        mMediaRouter.addCallback(mMediaRouteSelector, mMediaRouterCallback,
                MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY);


        Log.d(TAG, "menu_search : " + mSearch);

        mSearch.setOnServiceFoundListener(
                new Search.OnServiceFoundListener() {
                    @Override
                    public void onFound(Service service) {
                        Log.d(TAG, "Search.onFound() service : " + service.toString());
                        if (!mDeviceList.contains(service)) {
                            mDeviceList.add(service);
                            Map<String,String> device = new HashMap<String, String>();
                            device.put("name",service.getName());
                            device.put("type",service.getType());
                            mDeviceInfos.add(device);
//                            mTVListAdapter.add(service.getName());
                            mTVListAdapter.notifyDataSetChanged();
                        }

                    }
                }
        );

        mSearch.setOnServiceLostListener(
                new Search.OnServiceLostListener() {
                    @Override
                    public void onLost(Service service) {
                        Log.d(TAG, "Search.onLost() service : " + service.toString());
                        mDeviceList.remove(service);
                        mDeviceInfos.remove(service.getName());
//                        mTVListAdapter.remove(service.getName());
                        mTVListAdapter.notifyDataSetChanged();
                    }
                }
        );

        // You can connect getByURI,getById
        // This is example code.
        /*
        Service.getByURI(Uri.parse("http://192.168.0.68:8001/api/v2/"), new Result<Service>() {
            @Override
            public void onSuccess(Service service) {
                mService = service;
                mApplication = mService.createApplication(mApplicationId, mChannelId);
                addAllListener(mApplication);
                mApplication.connect(new Result<Client>() {
                    @Override
                    public void onSuccess(Client client) {
                        mApplication.setDebug(true);
                        Log.d(TAG, "application.connect onSuccess " + client.toString());
                    }

                    @Override
                    public void onError(com.samsung.multiscreen.Error error) {
                        Log.d(TAG, "application.connect onError " + error.toString());
                        Toast.makeText(mContext, "Launch TV app error occurs : " + error.toString(), Toast.LENGTH_LONG).show();
                    }
                });
            }

            @Override
            public void onError(Error error) {
                Log.d(TAG, "Service.getById  onError : ");
            }
        });

        Service.getById(mContext, "uuid:8304f069-639d-41a7-afcd-6bf8160dd88e", new Result<Service>() {
            @Override
            public void onSuccess(Service service) {
                mService = service;
                mApplication = mService.createApplication(mApplicationId, mChannelId);
                addAllListener(mApplication);
                mApplication.connect(new Result<Client>() {
                    @Override
                    public void onSuccess(Client client) {
                        mApplication.setDebug(true);
                        Log.d(TAG, "application.connect onSuccess " + client.toString());
                    }

                    @Override
                    public void onError(com.samsung.multiscreen.Error error) {
                        Log.d(TAG, "application.connect onError " + error.toString());
                        Toast.makeText(mContext, "Launch TV app error occurs : " + error.toString(), Toast.LENGTH_LONG).show();
                    }
                });
            }

            @Override
            public void onError(Error error) {
                Log.d(TAG, "Service.getById  onError : ");
            }
        });
        */

        alertBuilder.setAdapter(mTVListAdapter,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        setRefreshActionButtonState(CONNECTING_DEVICE);
                        mSearch.stop();

                        // End media router discovery
                        mMediaRouter.removeCallback(mMediaRouterCallback);
                        mFullScreen = false;
                        //Save Service

                        //String cName = mTVListAdapter.getItem(which).getClass().getName();
                        String cName = mDeviceList.get(which).getClass().getName();
                        Log.d(TAG, "cName : " + cName);
                        Log.d(TAG, "mService : " + Service.class.getName());
                        Log.d(TAG, "castDevice : " + CastDevice.class.getName());

                        if (Service.class.getName().equals(cName)) {
                            //MSF
                            setConnectionDeviceType(Service.class.getName());
                            mService = (Service) mDeviceList.get(which);
                            if (mApplication == null) {

                                mApplication = mService.createApplication(mApplicationId, mChannelId);
                                addAllListener(mApplication);
                                Log.d(TAG, "createApplications mApplicationId : " + mApplicationId + "mChannelId : " + mChannelId);

                                mApplication.connect(new Result<Client>() {
                                    @Override
                                    public void onSuccess(Client client) {
                                        mApplication.setDebug(true);
                                        Log.d(TAG, "application.connect onSuccess " + client.toString());
                                    }

                                    @Override
                                    public void onError(com.samsung.multiscreen.Error error) {
                                        Log.d(TAG, "application.connect onError " + error.toString());
                                        Toast.makeText(mContext, "Launch TV app error occurs : " + error.toString(), Toast.LENGTH_LONG).show();
                                        mApplication=null;
                                        mService=null;
                                        if(error.getCode() == 404){
                                            setRefreshActionButtonState(DISCONNECT_DEVICE);
                                        }

//                                        // Check Tv app exist in samsung apps Server or not.
//                                        // install onError can support 2016 TV now, but
//                                        mApplication.install(new Result<Boolean>() {
//                                            @Override
//                                            public void onSuccess(Boolean aBoolean) {
//                                                Log.d(TAG, "application.install onSuccess " + aBoolean.toString());
//                                                Toast.makeText(mContext, "install  TV app onSuccess : " + aBoolean.toString(), Toast.LENGTH_LONG).show();
//                                            }
//
//                                            @Override
//                                            public void onError(Error error) {
//                                                if(error.getCode() == 404){
//                                                    Log.d(TAG, "application.install onError ");
//                                                    Toast.makeText(mContext, "TV APP is not existed in TV app Server : ", Toast.LENGTH_LONG).show();
//                                                }
//                                            }
//                                        });
                                    }
                                });
                            }
                            Log.d(TAG, "onClick : mService = " + mService.toString());

                            mApplication.addOnMessageListener("control_TV", new Application.OnMessageListener() {
                                @Override
                                public void onMessage(Message message) {
                                    Log.d(TAG, "addOnMessageListener event control :  " + message.toString());
                                }
                            });

                            mApplication.addOnMessageListener("play_TV", new Application.OnMessageListener() {
                                @Override
                                public void onMessage(Message message) {
                                    //CreateControlDialog();
                                    Log.d(TAG, "addOnMessageListener event PLAY :  " + message.toString());
                                }
                            });
                        } else if (CastDevice.class.getName().equals(cName)) {
                            //Google Cast
                            setConnectionDeviceType(CastDevice.class.getName());
                            mCastSelectedDevice = (CastDevice) mDeviceList.get(which);
                            launchReceiver();

                        }
                    }
                });

        alertBuilder.show();

    }

    private void CreateDisconnectTvDialogDialog() {
        AlertDialog.Builder alertBuilder = new AlertDialog.Builder(this);
        alertBuilder.setIcon(R.drawable.ic_cast_connected_white_24dp);
        alertBuilder.setTitle(getDeviceName());

        LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View layout = inflater.inflate(R.layout.disconnect_dialog, (ViewGroup) findViewById(R.id.disconnect_root));

        // set dialog message
        alertBuilder.setNeutralButton(R.string.disconnect_device, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Log.d(TAG, "CreateDisconnectTvDialogDialog onClick  type : " + getConnectDeviceType());
                String connectionType = getConnectDeviceType();
                if (Service.class.getName().equals(connectionType)) {
                    disconnectTV(true, false);
                } else if (CastDevice.class.getName().equals(connectionType)) {
                    teardown(true);
                }
            }
        });

//        alertBuilder.setView(layout);
        alertBuilder.show();
    }

    private void CreateControlDialog() {
        AlertDialog.Builder alertBuilder = new AlertDialog.Builder(this);
        alertBuilder.setIcon(R.drawable.ic_sysbar_quicksettings);
        alertBuilder.setTitle("Control");

        LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View layout = inflater.inflate(R.layout.control_dialog, (ViewGroup) findViewById(R.id.control_root));

        alertBuilder.setView(layout);
        alertBuilder.show();
    }

    private void CreateSearchDataDialog() {
        AlertDialog.Builder alertBuilder = new AlertDialog.Builder(this);
//        alertBuilder.setIcon(R.drawable.ic_sysbar_quicksettings);
//        alertBuilder.setTitle("Control");

        LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View layout = inflater.inflate(R.layout.search_data_dialog, (ViewGroup) findViewById(R.id.layout_search));


        alertBuilder.setView(layout);

        final EditText userInput = (EditText) layout
                .findViewById(R.id.editTextDialogUserInput);

        // set dialog message
        alertBuilder
                .setCancelable(false)
                .setPositiveButton("OK",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                // get user input and set it to result
                                // edit text
                                //Search data from userInput.getText()

                                onQueryTextChanged(userInput.getText().toString());
                            }
                        })
                .setNegativeButton("Cancel",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                dialog.cancel();
                            }
                        });


        alertBuilder.show();


    }

    public void onControlbuttonClick(View v) {
        Log.d(TAG, "onControlbuttonClick :  ");
        if (mApplication != null) {
            Log.d(TAG, "control    addOnMessageListener");

            switch (v.getId()) {
                case R.id.stop:
                    mApplication.publish("control", "stop");
                    return;

                case R.id.pause:
                    mApplication.publish("control", "pause");
                    return;

                case R.id.play:
                    mApplication.publish("control", "play");
                    return;

                case R.id.ff:
                    mApplication.publish("control", "ff");
                    return;

                case R.id.rew:
                    mApplication.publish("control", "rew");
                    return;

                case R.id.fullscreen:

                    if (mFullScreen) {
                        mFullScreen = false;
                        mApplication.publish("control", "originScreen");

                    } else {
                        mFullScreen = true;
                        mApplication.publish("control", "fullScreen");
                    }
                    return;

            }
        }
    }

    public void initImageLoader(Context context) {
        ImageLoaderConfiguration.Builder config = new ImageLoaderConfiguration.Builder(context);
        config.threadPriority(Thread.NORM_PRIORITY - 2)
                .denyCacheImageMultipleSizesInMemory()
                .diskCacheFileNameGenerator(new Md5FileNameGenerator())
                .diskCacheSize(3 * 1024 * 1024)
                .tasksProcessingOrder(QueueProcessingType.LIFO)
                .writeDebugLogs();

        ImageLoader.getInstance().init(config.build());
    }

    public void disconnectTV(boolean stop, boolean destory) {
        Log.d(TAG, "disconnect  status = " + mConnectSatus.toString());
        if (CONNECT_DEVICE.equals(mConnectSatus)) {

            setRefreshActionButtonState(DISCONNECT_DEVICE);
            mApplication.disconnect(stop, new Result<Client>() {
                @Override
                public void onSuccess(Client client) {
                    Log.d(TAG, "disconnect - onSuccess : " + client.toString());
                    Toast.makeText(mContext, "Disconnect succussfully.", Toast.LENGTH_LONG).show();
                    if(mApplication != null) {
                        //mApplication.removeAllListeners();
                        mService = null;
                        mApplication = null;
                    }

                }

                @Override
                public void onError(Error error) {
                    Log.d(TAG, "disconnect - onError : " + error.toString());
                }
            });
        } else {
            if (!destory)
                Toast.makeText(mContext, "Make connection first", Toast.LENGTH_LONG).show();
        }
    }


    public void addAllListener(Application app) {

        app.setOnConnectListener(new Channel.OnConnectListener() {
            @Override
            public void onConnect(Client client) {
                Log.d(TAG, "onConnect - client : " + client.toString());
                setRefreshActionButtonState(CONNECT_DEVICE);
            }
        });
        app.setOnDisconnectListener(new Channel.OnDisconnectListener() {
            @Override
            public void onDisconnect(Client client) {
                Log.d(TAG, "onDisconnect - client : " + client.toString());
                Toast.makeText(mContext, "Disconnect TV", Toast.LENGTH_LONG).show();
                setRefreshActionButtonState(DISCONNECT_DEVICE);
                mService = null;
                mApplication = null;
            }
        });

        app.setOnClientConnectListener(new Channel.OnClientConnectListener() {
            @Override
            public void onClientConnect(Client client) {
                Log.d(TAG, "onClientConnect - client : " + client.toString());
            }
        });
        app.setOnClientDisconnectListener(new Channel.OnClientDisconnectListener() {
            @Override
            public void onClientDisconnect(Client client) {
                Log.d(TAG, "onClientDisconnect - client : " + client.toString());
            }
        });

        app.setOnErrorListener(new Channel.OnErrorListener() {
            @Override
            public void onError(Error error) {
                Log.d(TAG, "onError - error : " + error.toString());
                Toast.makeText(mContext, "connection error occurs : " + error.toString(), Toast.LENGTH_LONG).show();
            }
        });

        app.setOnReadyListener(new Channel.OnReadyListener() {
            @Override
            public void onReady() {
                Log.d(TAG, "onReady -  : ");
            }
        });
    }

    public void setConnectionStatus(String status){
        mConnectSatus = status;
    }

    private String getDeviceName(){
        if(mService != null){
            return mService.getName();
        }else if(mCastSelectedDevice !=null){
            return mCastSelectedDevice.getFriendlyName();
        }
        return  "";
    }

    private void setConnectionDeviceType(String type){
        mConnectionDeviceType = type;
    }
    private String getConnectDeviceType(){
        return mConnectionDeviceType;
    }

    //////////////////////////////////////////////////////////////////
    // Chrome CAST API
    //////////////////////////////////////////////////////////////////
    /**
     * For intergrate Google Cast Device discover
     * Callback for MediaRouter events
     */
    private class MyMediaRouterCallback extends MediaRouter.Callback {

        @Override
        public void onRouteAdded(MediaRouter router, MediaRouter.RouteInfo route) {

            CastDevice castDevice = CastDevice.getFromBundle(route.getExtras());
            Log.d(TAG, "onRouteAdded CastDevice = " + route.toString());
            if(!mDeviceList.contains(castDevice)) {
                mDeviceList.add(castDevice);
                Map<String,String> device = new HashMap<String, String>();
                device.put("name",route.getName());
                device.put("type",route.getDescription());
                mDeviceInfos.add(device);
//                mTVListAdapter.add(castDevice.getFriendlyName());
                mTVListAdapter.notifyDataSetChanged();
            }
        }

        @Override
        public void onRouteChanged(MediaRouter router, MediaRouter.RouteInfo route) {

            CastDevice castDevice = CastDevice.getFromBundle(route.getExtras());
            Log.d(TAG, "onRouteChanged CastDevice = " + route.toString());
            if(!mDeviceList.contains(castDevice)) {
                mDeviceList.add(castDevice);
                Map<String,String> device = new HashMap<String, String>();
                device.put("name",route.getName());
                device.put("type",route.getDescription());
                mDeviceInfos.add(device);
//                mTVListAdapter.add(castDevice.getFriendlyName());
                mTVListAdapter.notifyDataSetChanged();
            }
        }

        @Override
        public void onRouteRemoved(MediaRouter router, MediaRouter.RouteInfo route) {
            Log.d(TAG, "onRouteRemoved");
            CastDevice castDevice = CastDevice.getFromBundle(route.getExtras());
            mDeviceList.remove(castDevice);
            mDeviceInfos.remove(route.getName());
//            mTVListAdapter.remove(castDevice.getFriendlyName());
            mTVListAdapter.notifyDataSetChanged();
        }

        @Override
        public void onRouteSelected(MediaRouter router, MediaRouter.RouteInfo info) {
            Log.d(TAG, "onRouteSelected -- not use");
            Log.d(TAG, "onRouteSelected");

        }

        @Override
        public void onRouteUnselected(MediaRouter router, MediaRouter.RouteInfo info) {
            Log.d(TAG, "onRouteUnselected ---not use");

        }
    }


    /**
     * Start the receiver app
     */
    private void launchReceiver() {
        try {
            mCastListener = new Cast.Listener() {

                @Override
                public void onApplicationDisconnected(int errorCode) {
                    Log.d(TAG, "application has stopped");
                }

            };

            // Connect to Google Play services
            mConnectionCallbacks = new ConnectionCallbacks();
            mConnectionFailedListener = new ConnectionFailedListener();
            Cast.CastOptions.Builder apiOptionsBuilder = Cast.CastOptions
                    .builder(mCastSelectedDevice, mCastListener);
            mApiClient = new GoogleApiClient.Builder(this)
                    .addApi(Cast.API, apiOptionsBuilder.build())
                    .addConnectionCallbacks(mConnectionCallbacks)
                    .addOnConnectionFailedListener(mConnectionFailedListener)
                    .build();

            mApiClient.connect();
        } catch (Exception e) {
            Log.e(TAG, "Failed launchReceiver", e);
        }
    }

    /**
     * Google Play services callbacks
     */
    private class ConnectionCallbacks implements GoogleApiClient.ConnectionCallbacks {

        @Override
        public void onConnected(Bundle connectionHint) {
            Log.d(TAG, "onConnected");


            if (mApiClient == null) {
                // We got disconnected while this runnable was pending
                // execution.
                return;
            }

            {
                // Launch the receiver app
                Cast.CastApi.launchApplication(mApiClient,CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID, false)//"794B7BBF"
                        .setResultCallback(
                                new ResultCallback<Cast.ApplicationConnectionResult>() {
                                    @Override
                                    public void onResult(
                                            Cast.ApplicationConnectionResult result) {
                                        Status status = result.getStatus();
                                        Log.d(TAG,
                                                "ApplicationConnectionResultCallback.onResult:"
                                                        + status.getStatusCode());
                                        if (status.isSuccess()) {
                                            setRefreshActionButtonState(CONNECT_DEVICE);
                                            Log.e(TAG, "application launch");

                                        } else {
                                            setRefreshActionButtonState(DISCONNECT_DEVICE);
                                            Log.e(TAG, "application could not launch");

                                        }

                                        // get Media channel
                                        try {
                                            Cast.CastApi.setMessageReceivedCallbacks(mApiClient,
                                                    mRemoteMediaPlayer.getNamespace(), mRemoteMediaPlayer);
                                        } catch (IOException e) {
                                            Log.e(TAG, "Exception while creating media channel", e);
                                        }
                                        mRemoteMediaPlayer
                                                .requestStatus(mApiClient)
                                                .setResultCallback(
                                                        new ResultCallback<RemoteMediaPlayer.MediaChannelResult>() {
                                                            @Override
                                                            public void onResult(RemoteMediaPlayer.MediaChannelResult result) {
                                                                if (!result.getStatus().isSuccess()) {
                                                                    Log.e(TAG, "Failed to request status.");
                                                                }
                                                            }
                                                        });
                                    }
                                });
            }
        }

        @Override
        public void onConnectionSuspended(int cause) {
            Log.d(TAG, "onConnectionSuspended");

        }
    }

    /**
     * Google Play services callbacks
     */
    private class ConnectionFailedListener implements GoogleApiClient.OnConnectionFailedListener {

        @Override
        public void onConnectionFailed(ConnectionResult result) {
            Log.e(TAG, "onConnectionFailed ");

        }
    }

    /**
     * Tear down the connection to the receiver
     */
    private void teardown(boolean selectDefaultRoute) {
        Log.d(TAG, "teardown");
        setRefreshActionButtonState(DISCONNECT_DEVICE);
        if (mApiClient != null) {

            if (mApiClient.isConnected() || mApiClient.isConnecting()) {

                Cast.CastApi.stopApplication(mApiClient, mSessionId);
                mApiClient.disconnect();
            }


            mApiClient = null;
        }
        if (selectDefaultRoute) {
            mMediaRouter.selectRoute(mMediaRouter.getDefaultRoute());
        }
        mCastSelectedDevice = null;
        mSessionId = null;
    }


}

