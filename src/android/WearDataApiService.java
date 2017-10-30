package us.m4rc.cordova.androidwear.dataapi;

import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItemBuffer;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

public class WearDataApiService extends WearableListenerService
    implements GoogleApiClient.ConnectionCallbacks {

  public static WearDataApiService WearDataApiServiceSingleton = null;
  private final String TAG = WearDataApiService.class.getSimpleName();
  private GoogleApiClient mGoogleApiClient;

  @Override
  public void onConnected(@Nullable Bundle bundle) {
    Log.d(TAG, "Google API Client connected");
    if (WearDataApiPlugin.WearDataApiPluginSingleton != null) {
      WearDataApiPlugin.WearDataApiPluginSingleton.onServiceStarted();
    }
  }

  @Override
  public void onConnectionSuspended(int i) {}

  @Override
  public void onCreate() {
    super.onCreate();

    // set up the Google API client
    if (mGoogleApiClient==null) {
      mGoogleApiClient = new GoogleApiClient.Builder(this)
        .addApi(Wearable.API)
        .addConnectionCallbacks(this)
        .build();
    }
    if (!mGoogleApiClient.isConnected()) {
      mGoogleApiClient.connect();
    }
    Log.d(TAG, "WearDataApiService started.");
    WearDataApiServiceSingleton = this;
  }

  @Override
  public void onDestroy() {
    // clean up Google API client
    if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
      mGoogleApiClient.disconnect();
    }
    Log.d(TAG, "WearDataApiService shut down.");
    WearDataApiServiceSingleton = null; // if we are shutdown gracefully
    super.onDestroy();
  }

  /**
   * Forwards data changed to the plugin for callback to Cordova
   */
  @Override
  public void onDataChanged(DataEventBuffer dataEventBuffer) {
    if (WearDataApiPlugin.WearDataApiPluginSingleton != null) {
      String data = "";

      for (DataEvent event : dataEventBuffer) {
        if (event.getType() == DataEvent.TYPE_CHANGED &&
                event.getDataItem().getUri().getPath().equals("/fitsix/stats")) {
          DataMapItem dataMapItem = DataMapItem.fromDataItem(event.getDataItem());
          Asset profileAsset = dataMapItem.getDataMap().getAsset("fitsixStats");
          data = loadFileFromAsset(profileAsset);
          try {
            WearDataApiPlugin.WearDataApiPluginSingleton.onDataChanged(data);
          } catch (JSONException e) {
            e.printStackTrace();
          }
        }
      }
    } else {
      Log.d(TAG, "onDataChanged but no DataListeners.");
    }
  }

  private String loadFileFromAsset(Asset asset) {
    if (asset == null) {
      throw new IllegalArgumentException("Asset must be non-null");
    }
    ConnectionResult result =
            mGoogleApiClient.blockingConnect(1000, TimeUnit.MILLISECONDS);
    if (!result.isSuccess()) {
      return "";
    }
    InputStream assetInputStream = Wearable.DataApi.getFdForAsset(
            mGoogleApiClient, asset).await().getInputStream();
//        mGoogleApiClient.disconnect();
    if (assetInputStream == null) {
      Log.w(TAG, "Requested an unknown Asset.");
      return "";
    }
    return convertStreamToString(assetInputStream);
  }

  private String convertStreamToString(java.io.InputStream is) {
    java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
    return s.hasNext() ? s.next() : "";
  }


  // methods below form a thin wrapper around the DataApi for use in the Plugin
  public PendingResult<DataApi.DataItemResult> putDataRequest(PutDataRequest data) {
    return Wearable.DataApi.putDataItem(mGoogleApiClient, data);
  }

  public PendingResult<DataApi.DeleteDataItemsResult> deleteDataItems(Uri uri, int filterType) {
    return Wearable.DataApi.deleteDataItems(mGoogleApiClient, uri, filterType);
  }

  public PendingResult<DataItemBuffer> getDataItems(Uri uri, int filterType) {
    return Wearable.DataApi.getDataItems(mGoogleApiClient, uri, filterType);
  }

  @Override
  protected void finalize() throws Throwable {
    super.finalize();
    WearDataApiServiceSingleton = null; // when we are garbage collected
  }
}
