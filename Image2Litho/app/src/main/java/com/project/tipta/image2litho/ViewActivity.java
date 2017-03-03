package com.project.tipta.image2litho;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Parcelable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.Toast;

import com.project.tipta.image2litho.stlbean.STLObject;
import com.project.tipta.image2litho.util.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class ViewActivity extends AppCompatActivity {

    private STLView stlView = null;
    private FrameLayout relativeLayout;
    private STLObject stlObject;
    private Context context;
    private byte[] stlBytes;

    private ImageButton download;
    private ProgressDialog mProgressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view);

        context = this;
        relativeLayout = (FrameLayout) findViewById(R.id.stlFrameLayout);
        download = (ImageButton) findViewById(R.id.downloadButton);

        final String stl_path = getFilesDir() + File.separator + "temp.stl";
        File file = new File(stl_path);
        openfile(file);

        download.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startDownload(stl_path);
            }
        });
    }

    private void startDownload(String stl_path) {
        new DownloadFileAsync().execute(stl_path);
    }

    class DownloadFileAsync extends AsyncTask<String, String, String> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            mProgressDialog = new ProgressDialog(context);
            mProgressDialog.setMessage("Downloading file...");
            mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            mProgressDialog.setIndeterminate(true);
            mProgressDialog.setCancelable(false);
            mProgressDialog.show();
        }

        @Override
        protected String doInBackground(String... stl_path) {
            try {
                Long tsLong = System.currentTimeMillis()/1000;
                String ts = tsLong.toString();
                String baseDir = Environment.getExternalStorageDirectory() + "/Download/";
                String fileName = "image2litho" + ts + ".stl";

                File infile = new File(stl_path[0]);
                File outfile = new File(baseDir + File.separator + fileName);
                InputStream in = new FileInputStream(infile);
                OutputStream out = new FileOutputStream(outfile);

                Long lenghtOfFile = infile.length();
                int totalWritten = 0;
                int bufferedBytes = 0;

                // Transfer bytes from in to out
                byte[] buffer = new byte[1024];
                while ((bufferedBytes = in.read(buffer)) > 0) {
                    totalWritten += bufferedBytes;
                    publishProgress(Integer.toString((int) ((totalWritten * 100) / lenghtOfFile)));
                    out.write(buffer, 0, bufferedBytes);
                }
                in.close();
                out.close();

            } catch (IOException e) { e.printStackTrace(); }
            return null;
        }
        protected void onProgressUpdate(String... progress) {
            mProgressDialog.setIndeterminate(false);
            mProgressDialog.setProgress(Integer.parseInt(progress[0]));
        }

        @Override
        protected void onPostExecute(String unused) {
            mProgressDialog.dismiss();
            Toast toast = Toast.makeText(getApplicationContext(),
                    "Downloaded!", Toast.LENGTH_SHORT);
            toast.setGravity(Gravity.CENTER, 0, 0);
            toast.show();
            mProgressDialog = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (stlView != null) {
            com.project.tipta.image2litho.util.Log.i("onResume");
            // stlView.onResume();
            stlView.requestRedraw();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (stlView != null) {
            com.project.tipta.image2litho.util.Log.i("onPause");
            // stlView.onPause();
        }
    }

    //Restore Instance State
    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        com.project.tipta.image2litho.util.Log.i("onRestoreInstanceState");
        Parcelable stlFileName = savedInstanceState
                .getParcelable("STLFileName");
        stlBytes=savedInstanceState.getByteArray("stlBytes");
        if(stlBytes!=null){
            stlObject = new STLObject(stlBytes, this, new ViewActivity.FinishSTL());
        }

    }

    //Save Instance State
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (stlView != null) {
            com.project.tipta.image2litho.util.Log.i("onSaveInstanceState");
            outState.putParcelable("STLFileName", stlView.getUri());
            outState.putBoolean("isRotate", stlView.isRotate());
            outState.putByteArray("stlBytes", stlBytes);
        }
    }

    // get stl bytes
    private byte[] getSTLBytes(Context context, Uri uri) {
        byte[] stlBytes = null;
        InputStream inputStream = null;
        try {
            inputStream = context.getContentResolver().openInputStream(uri);
            stlBytes = IOUtils.toByteArray(inputStream);
        } catch (IOException e) {
        } finally {
            IOUtils.closeQuietly(inputStream);
        }
        return stlBytes;
    }

    //call back
    class FinishSTL implements STLObject.IFinishCallBack {

        @Override
        public void readstlfinish() {
            // TODO Auto-generated method stub
            if (stlObject != null) {
                if (stlView == null) {
                    stlView = new STLView(context, stlObject);
                    relativeLayout.addView(stlView);
                } else {
                    stlView.setNewSTLObject(stlObject);
                }
            }
        }
    }

    //open stl file
    public void openfile(File file){
        System.out.println("file size:"+file.length()/1024/1024+"M");
        if(file.length()>10*1024*1024){
            Toast.makeText(this, "file size bigger than 10M, maybe load fail", Toast.LENGTH_SHORT).show();
        }
        SharedPreferences config = getSharedPreferences("PathSetting",
                Activity.MODE_PRIVATE);
        SharedPreferences.Editor configEditor = config.edit();
        configEditor.putString("lastPath", file.getParent());
        configEditor.commit();
        // initiate data
        if(stlView!=null){
            relativeLayout.removeAllViews();
            stlView.delete();
            stlView=null;
            stlBytes = null;
            System.gc();
        }
        try {
            stlBytes = getSTLBytes(this, Uri.fromFile(file));
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.error_outbig),
                    Toast.LENGTH_LONG).show();
        }

        if (stlBytes == null) {
            Toast.makeText(this, getString(R.string.error_fetch_data),
                    Toast.LENGTH_LONG).show();
            return;
        }

        stlObject = new STLObject(stlBytes, this, new ViewActivity.FinishSTL());
    }
}
