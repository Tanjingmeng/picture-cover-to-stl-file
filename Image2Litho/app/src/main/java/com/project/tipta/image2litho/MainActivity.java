package com.project.tipta.image2litho;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.AsyncTask;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.net.Uri;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.io.*;
import java.util.Iterator;

import toxi.geom.*;
import toxi.geom.mesh.*;

public class MainActivity extends AppCompatActivity implements SeekBar.OnSeekBarChangeListener {

    private int PICK_IMAGE_REQUEST = 1;
    private int MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 2;
    private int MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE = 3;
    private int MY_PERMISSIONS_REQUEST_SYSTEM_ALERT_WINDOW = 4;

    private Setting setting = new Setting();

    private ImageButton imageSelect;
    private Switch positive;
    private SeekBar maxSize, thickness, border, thinnestLayer, pixelPerMM;
    private TextView tMaxSize, tThickness, tBorder, tThinnestLayer, tPixelPerMM;
    private Button generateButton;

    private ProgressDialog mProgressDialog;
    private Context context;

    private Bitmap imageRaw = null;
    private int process = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        context = this;

        imageSelect = (ImageButton) findViewById(R.id.downloadButton);
        positive = (Switch) findViewById(R.id.switchPositive);
        maxSize = (SeekBar) findViewById(R.id.seekBarMaximumSize);
        thickness = (SeekBar) findViewById(R.id.seekBarThickness);
        border = (SeekBar) findViewById(R.id.seekBarBorder);
        thinnestLayer = (SeekBar) findViewById(R.id.seekBarThinnestLayer);
        pixelPerMM = (SeekBar) findViewById(R.id.seekBarPixelPerMM);
        tMaxSize = (TextView) findViewById(R.id.textViewMaximumSize);
        tThickness = (TextView) findViewById(R.id.textViewThickness);
        tBorder = (TextView) findViewById(R.id.textViewBorder);
        tThinnestLayer = (TextView) findViewById(R.id.textViewThinnestLayer);
        tPixelPerMM = (TextView) findViewById(R.id.textViewPixelPerMM);
        generateButton = (Button) findViewById(R.id.buttonGenerate);

        maxSize.setOnSeekBarChangeListener(this);
        thickness.setOnSeekBarChangeListener(this);
        border.setOnSeekBarChangeListener(this);
        thinnestLayer.setOnSeekBarChangeListener(this);
        pixelPerMM.setOnSeekBarChangeListener(this);

        positive.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (buttonView.isChecked()){
                    setting.setPositive(false);
                    Log.d("Positive", String.valueOf(setting.isPositive()));
                }
                else{
                    setting.setPositive(true);
                    Log.d("Positive", String.valueOf(setting.isPositive()));
                }
            }
        });

        imageSelect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent();
                intent.setType("image/*");
                intent.setAction(Intent.ACTION_GET_CONTENT);
                startActivityForResult(Intent.createChooser(intent, "Select Picture"), PICK_IMAGE_REQUEST);
            }
        });

        generateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startConvertion();
            }
        });

        getPermissions();
    }

    private void startConvertion() {
        new convertAsync().execute();
    }

    class convertAsync extends AsyncTask<String, String, String> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            mProgressDialog = new ProgressDialog(context);
            mProgressDialog.setMessage("Constructing...");
            mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            mProgressDialog.setIndeterminate(true);
            mProgressDialog.setCancelable(false);
            mProgressDialog.show();
        }

        @Override
        protected String doInBackground(String... str) {
            int newWidth = 0;
            int newHeight = 0;
            if (imageRaw.getWidth() > imageRaw.getHeight()) {
                newWidth = (int) (setting.getMax_size() - setting.getBorder() * 2) * setting.getPixel_per_mm() + 1;
                newHeight = (int)(imageRaw.getHeight()*((int)(setting.getMax_size() - setting.getBorder() * 2) *
                        setting.getPixel_per_mm() / (float)imageRaw.getWidth()) + 1);
            } else {
                newWidth = (int)(imageRaw.getWidth()*((int)(setting.getMax_size() - setting.getBorder() * 2) *
                        setting.getPixel_per_mm() / (float)imageRaw.getHeight()) + 1);
                newHeight = (int) (setting.getMax_size() - setting.getBorder() * 2) * setting.getPixel_per_mm() + 1;
            }
            mProgressDialog.setIndeterminate(false);
            process = 1;
            publishProgress();
            Bitmap imgScaled = scaleImage(imageRaw, newWidth, newHeight);
            mProgressDialog.setProgress(10);
            process = 2;
            publishProgress();
            Bitmap grayscaleImage = grayscaleImage(imgScaled, setting.isPositive());
            process = 3;
            mProgressDialog.setProgress(20);
            publishProgress();
            //outputSTL(grayscaleImage);

            Bitmap original = grayscaleImage;

            int[] rawInput = new int[original.getHeight() * original.getWidth()];
            original.getPixels(rawInput, 0, original.getWidth(), 0, 0, original.getWidth(), original.getHeight());

            TriangleMesh mesh;
            mesh = new TriangleMesh();

            //height quantization level
            int quantizationLevel = 1;

            float heightRange = setting.getThickness() - setting.getThinnest_layer();
            float heightLVL = heightRange / (255.0f * quantizationLevel);
            float xLVL = 1.0f/setting.getPixel_per_mm();
            float yLVL = 1.0f/setting.getPixel_per_mm();

            //heights initiation
            int[] heights = new int[original.getHeight() * original.getWidth()];
            for (int i = 0; i < original.getWidth() * original.getHeight(); i++) {
                heights[i] = Color.red(rawInput[i]);
            }

            //model width and height...
            float modelWidth = (original.getWidth() - 1) / setting.getPixel_per_mm() + 2 * setting.getBorder();
            float modelHeight = (original.getHeight() - 1) / setting.getPixel_per_mm() + 2 * setting.getBorder();

            //adding bottom...
            float x, y, z;
            Vec3D a, b, c, d;
            Vertex va, vb, vc, vd;

            x = 0;
            y = 0;
            z = 0;
            a = new Vec3D(x, y, z);

            x = modelWidth;
            y = 0;
            z = 0;
            b = new Vec3D(x, y, z);

            x = 0;
            y = modelHeight;
            z = 0;
            c = new Vec3D(x, y, z);

            x = modelWidth;
            y = modelHeight;
            z = 0;
            d = new Vec3D(x, y, z);

            va = new Vertex(a, 0);
            vb = new Vertex(b, 1);
            vc = new Vertex(c, 2);
            vd = new Vertex(d, 3);

            mesh.addFace(va, vb, vc);
            mesh.addFace(vb, vc, vd);

            //adding border...
            if (setting.getBorder() != 0.0f) {
                if (setting.isPositive()) {
                    //adding top border...
                    x = 0;
                    y = 0;
                    z = setting.getThickness();
                    a = new Vec3D(x, y, z);

                    x = modelWidth;
                    y = 0;
                    z = setting.getThickness();
                    b = new Vec3D(x, y, z);

                    x = modelWidth;
                    y = setting.getBorder();
                    z = setting.getThickness();
                    c = new Vec3D(x, y, z);

                    x = 0;
                    y = setting.getBorder();
                    z = setting.getThickness();
                    d = new Vec3D(x, y, z);

                    va = new Vertex(a, 0);
                    vb = new Vertex(b, 1);
                    vc = new Vertex(c, 2);
                    vd = new Vertex(d, 3);

                    mesh.addFace(va, vb, vc);
                    mesh.addFace(va, vc, vd);

                    x = 0;
                    y = modelHeight - setting.getBorder();
                    z = setting.getThickness();
                    a = new Vec3D(x, y, z);

                    x = modelWidth;
                    y = modelHeight - setting.getBorder();
                    z = setting.getThickness();
                    b = new Vec3D(x, y, z);

                    x = modelWidth;
                    y = modelHeight;
                    z = setting.getThickness();
                    c = new Vec3D(x, y, z);

                    x = 0;
                    y = modelHeight;
                    z = setting.getThickness();
                    d = new Vec3D(x, y, z);

                    va = new Vertex(a, 0);
                    vb = new Vertex(b, 1);
                    vc = new Vertex(c, 2);
                    vd = new Vertex(d, 3);

                    mesh.addFace(va, vb, vc);
                    mesh.addFace(va, vc, vd);

                    x = 0;
                    y = setting.getBorder();
                    z = setting.getThickness();
                    a = new Vec3D(x, y, z);

                    x = setting.getBorder();
                    y = setting.getBorder();
                    z = setting.getThickness();
                    b = new Vec3D(x, y, z);

                    x = setting.getBorder();
                    y = modelHeight - setting.getBorder();
                    z = setting.getThickness();
                    c = new Vec3D(x, y, z);

                    x = 0;
                    y = modelHeight - setting.getBorder();
                    z = setting.getThickness();
                    d = new Vec3D(x, y, z);

                    va = new Vertex(a, 0);
                    vb = new Vertex(b, 1);
                    vc = new Vertex(c, 2);
                    vd = new Vertex(d, 3);

                    mesh.addFace(va, vb, vc);
                    mesh.addFace(va, vc, vd);

                    x = modelWidth - setting.getBorder();
                    y = setting.getBorder();
                    z = setting.getThickness();
                    a = new Vec3D(x, y, z);

                    x = modelWidth;
                    y = setting.getBorder();
                    z = setting.getThickness();
                    b = new Vec3D(x, y, z);

                    x = modelWidth;
                    y = modelHeight - setting.getBorder();
                    z = setting.getThickness();
                    c = new Vec3D(x, y, z);

                    x = modelWidth - setting.getBorder();
                    y = modelHeight - setting.getBorder();
                    z = setting.getThickness();
                    d = new Vec3D(x, y, z);

                    va = new Vertex(a, 0);
                    vb = new Vertex(b, 1);
                    vc = new Vertex(c, 2);
                    vd = new Vertex(d, 3);

                    mesh.addFace(va, vb, vc);
                    mesh.addFace(va, vc, vd);

                    //adding side border...
                    x = 0;
                    y = 0;
                    z = 0;
                    a = new Vec3D(x, y, z);

                    x = modelWidth;
                    y = 0;
                    z = 0;
                    b = new Vec3D(x, y, z);

                    x = modelWidth;
                    y = 0;
                    z = setting.getThickness();
                    c = new Vec3D(x, y, z);

                    x = 0;
                    y = 0;
                    z = setting.getThickness();
                    d = new Vec3D(x, y, z);

                    va = new Vertex(a, 0);
                    vb = new Vertex(b, 1);
                    vc = new Vertex(c, 2);
                    vd = new Vertex(d, 3);

                    mesh.addFace(va, vb, vc);
                    mesh.addFace(va, vc, vd);

                    x = 0;
                    y = 0;
                    z = 0;
                    a = new Vec3D(x, y, z);

                    x = 0;
                    y = modelHeight;
                    z = 0;
                    b = new Vec3D(x, y, z);

                    x = 0;
                    y = modelHeight;
                    z = setting.getThickness();
                    c = new Vec3D(x, y, z);

                    x = 0;
                    y = 0;
                    z = setting.getThickness();
                    d = new Vec3D(x, y, z);

                    va = new Vertex(a, 0);
                    vb = new Vertex(b, 1);
                    vc = new Vertex(c, 2);
                    vd = new Vertex(d, 3);

                    mesh.addFace(va, vb, vc);
                    mesh.addFace(va, vc, vd);

                    x = 0;
                    y = modelHeight;
                    z = 0;
                    a = new Vec3D(x, y, z);

                    x = modelWidth;
                    y = modelHeight;
                    z = 0;
                    b = new Vec3D(x, y, z);

                    x = modelWidth;
                    y = modelHeight;
                    z = setting.getThickness();
                    c = new Vec3D(x, y, z);

                    x = 0;
                    y = modelHeight;
                    z = setting.getThickness();
                    d = new Vec3D(x, y, z);

                    va = new Vertex(a, 0);
                    vb = new Vertex(b, 1);
                    vc = new Vertex(c, 2);
                    vd = new Vertex(d, 3);

                    mesh.addFace(va, vb, vc);
                    mesh.addFace(va, vc, vd);

                    x = modelWidth;
                    y = 0;
                    z = 0;
                    a = new Vec3D(x, y, z);

                    x = modelWidth;
                    y = modelHeight;
                    z = 0;
                    b = new Vec3D(x, y, z);

                    x = modelWidth;
                    y = modelHeight;
                    z = setting.getThickness();
                    c = new Vec3D(x, y, z);

                    x = modelWidth;
                    y = 0;
                    z = setting.getThickness();
                    d = new Vec3D(x, y, z);

                    va = new Vertex(a, 0);
                    vb = new Vertex(b, 1);
                    vc = new Vertex(c, 2);
                    vd = new Vertex(d, 3);

                    mesh.addFace(va, vb, vc);
                    mesh.addFace(va, vc, vd);
                } else {
                    //adding top border...
                    x = 0;
                    y = 0;
                    z = setting.getThinnest_layer();
                    a = new Vec3D(x, y, z);

                    x = modelWidth;
                    y = 0;
                    z = setting.getThinnest_layer();
                    b = new Vec3D(x, y, z);

                    x = modelWidth;
                    y = setting.getBorder();
                    z = setting.getThinnest_layer();
                    c = new Vec3D(x, y, z);

                    x = 0;
                    y = setting.getBorder();
                    z = setting.getThinnest_layer();
                    d = new Vec3D(x, y, z);

                    va = new Vertex(a, 0);
                    vb = new Vertex(b, 1);
                    vc = new Vertex(c, 2);
                    vd = new Vertex(d, 3);

                    mesh.addFace(va, vb, vc);
                    mesh.addFace(va, vc, vd);

                    x = 0;
                    y = modelHeight - setting.getBorder();
                    z = setting.getThinnest_layer();
                    a = new Vec3D(x, y, z);

                    x = modelWidth;
                    y = modelHeight - setting.getBorder();
                    z = setting.getThinnest_layer();
                    b = new Vec3D(x, y, z);

                    x = modelWidth;
                    y = modelHeight;
                    z = setting.getThinnest_layer();
                    c = new Vec3D(x, y, z);

                    x = 0;
                    y = modelHeight;
                    z = setting.getThinnest_layer();
                    d = new Vec3D(x, y, z);

                    va = new Vertex(a, 0);
                    vb = new Vertex(b, 1);
                    vc = new Vertex(c, 2);
                    vd = new Vertex(d, 3);

                    mesh.addFace(va, vb, vc);
                    mesh.addFace(va, vc, vd);

                    x = 0;
                    y = setting.getBorder();
                    z = setting.getThinnest_layer();
                    a = new Vec3D(x, y, z);

                    x = setting.getBorder();
                    y = setting.getBorder();
                    z = setting.getThinnest_layer();
                    b = new Vec3D(x, y, z);

                    x = setting.getBorder();
                    y = modelHeight - setting.getBorder();
                    z = setting.getThinnest_layer();
                    c = new Vec3D(x, y, z);

                    x = 0;
                    y = modelHeight - setting.getBorder();
                    z = setting.getThinnest_layer();
                    d = new Vec3D(x, y, z);

                    va = new Vertex(a, 0);
                    vb = new Vertex(b, 1);
                    vc = new Vertex(c, 2);
                    vd = new Vertex(d, 3);

                    mesh.addFace(va, vb, vc);
                    mesh.addFace(va, vc, vd);

                    x = modelWidth - setting.getBorder();
                    y = setting.getBorder();
                    z = setting.getThinnest_layer();
                    a = new Vec3D(x, y, z);

                    x = modelWidth;
                    y = setting.getBorder();
                    z = setting.getThinnest_layer();
                    b = new Vec3D(x, y, z);

                    x = modelWidth;
                    y = modelHeight - setting.getBorder();
                    z = setting.getThinnest_layer();
                    c = new Vec3D(x, y, z);

                    x = modelWidth - setting.getBorder();
                    y = modelHeight - setting.getBorder();
                    z = setting.getThinnest_layer();
                    d = new Vec3D(x, y, z);

                    va = new Vertex(a, 0);
                    vb = new Vertex(b, 1);
                    vc = new Vertex(c, 2);
                    vd = new Vertex(d, 3);

                    mesh.addFace(va, vb, vc);
                    mesh.addFace(va, vc, vd);

                    //adding side border...
                    x = 0;
                    y = 0;
                    z = 0;
                    a = new Vec3D(x, y, z);

                    x = modelWidth;
                    y = 0;
                    z = 0;
                    b = new Vec3D(x, y, z);

                    x = modelWidth;
                    y = 0;
                    z = setting.getThinnest_layer();
                    c = new Vec3D(x, y, z);

                    x = 0;
                    y = 0;
                    z = setting.getThinnest_layer();
                    d = new Vec3D(x, y, z);

                    va = new Vertex(a, 0);
                    vb = new Vertex(b, 1);
                    vc = new Vertex(c, 2);
                    vd = new Vertex(d, 3);

                    mesh.addFace(va, vb, vc);
                    mesh.addFace(va, vc, vd);

                    x = 0;
                    y = 0;
                    z = 0;
                    a = new Vec3D(x, y, z);

                    x = 0;
                    y = modelHeight;
                    z = 0;
                    b = new Vec3D(x, y, z);

                    x = 0;
                    y = modelHeight;
                    z = setting.getThinnest_layer();
                    c = new Vec3D(x, y, z);

                    x = 0;
                    y = 0;
                    z = setting.getThinnest_layer();
                    d = new Vec3D(x, y, z);

                    va = new Vertex(a, 0);
                    vb = new Vertex(b, 1);
                    vc = new Vertex(c, 2);
                    vd = new Vertex(d, 3);

                    mesh.addFace(va, vb, vc);
                    mesh.addFace(va, vc, vd);

                    x = 0;
                    y = modelHeight;
                    z = 0;
                    a = new Vec3D(x, y, z);

                    x = modelWidth;
                    y = modelHeight;
                    z = 0;
                    b = new Vec3D(x, y, z);

                    x = modelWidth;
                    y = modelHeight;
                    z = setting.getThinnest_layer();
                    c = new Vec3D(x, y, z);

                    x = 0;
                    y = modelHeight;
                    z = setting.getThinnest_layer();
                    d = new Vec3D(x, y, z);

                    va = new Vertex(a, 0);
                    vb = new Vertex(b, 1);
                    vc = new Vertex(c, 2);
                    vd = new Vertex(d, 3);

                    mesh.addFace(va, vb, vc);
                    mesh.addFace(va, vc, vd);

                    x = modelWidth;
                    y = 0;
                    z = 0;
                    a = new Vec3D(x, y, z);

                    x = modelWidth;
                    y = modelHeight;
                    z = 0;
                    b = new Vec3D(x, y, z);

                    x = modelWidth;
                    y = modelHeight;
                    z = setting.getThinnest_layer();
                    c = new Vec3D(x, y, z);

                    x = modelWidth;
                    y = 0;
                    z = setting.getThinnest_layer();
                    d = new Vec3D(x, y, z);

                    va = new Vertex(a, 0);
                    vb = new Vertex(b, 1);
                    vc = new Vertex(c, 2);
                    vd = new Vertex(d, 3);

                    mesh.addFace(va, vb, vc);
                    mesh.addFace(va, vc, vd);
                }
            }
            mProgressDialog.setProgress(30);
            //picture mesh ....
            for (int i = 0; i < original.getWidth() - 1; i++) {
                for (int j = 0; j < original.getHeight() - 1; j++) {
                    x = modelWidth - (i * xLVL + setting.getBorder());
                    y = j * yLVL + setting.getBorder();
                    z = quantizationLevel * heightLVL * heights[i + j * original.getWidth()] + setting.getThinnest_layer();
                    a = new Vec3D(x, y, z);

                    x = modelWidth - ((i + 1) * xLVL + setting.getBorder());
                    y = j * yLVL + setting.getBorder();
                    z = quantizationLevel * heightLVL * heights[i + 1 + j * original.getWidth()] + setting.getThinnest_layer();
                    b = new Vec3D(x, y, z);

                    x = modelWidth - (i * xLVL + setting.getBorder());
                    y = (j + 1) * yLVL + setting.getBorder();
                    z = quantizationLevel * heightLVL * heights[i + (j + 1) * original.getWidth()] + setting.getThinnest_layer();
                    c = new Vec3D(x, y, z);

                    x = modelWidth - ((i + 1) * xLVL + setting.getBorder());
                    y = (j + 1) * yLVL + setting.getBorder();
                    z = quantizationLevel * heightLVL * heights[(i + 1) + (j + 1) * original.getWidth()] + setting.getThinnest_layer();
                    d = new Vec3D(x, y, z);

                    va = new Vertex(a, 0);
                    vb = new Vertex(b, 1);
                    vc = new Vertex(c, 2);
                    vd = new Vertex(d, 3);

                    mesh.addFace(va, vb, vc);
                    mesh.addFace(vb, vc, vd);
                }
            }
            mProgressDialog.setProgress(70);
            //adding side boarder between boarder and main picture...
            if (!setting.isPositive() || setting.getBorder() == 0.0f) {
                for (int i = 0; i < original.getWidth() - 1; i++) {
                    for (y = 0; y <= original.getHeight() - 1; y += original.getHeight() - 1) {
                        x = modelWidth - (i * xLVL + setting.getBorder());
                        z = quantizationLevel * heightLVL * heights[i + (int) y * original.getWidth()] + setting.getThinnest_layer();
                        a = new Vec3D(x, y / setting.getPixel_per_mm() + setting.getBorder(), z);

                        x = modelWidth - ((i + 1) * xLVL + setting.getBorder());
                        z = quantizationLevel * heightLVL * heights[i + 1 + (int) y * original.getWidth()] + setting.getThinnest_layer();
                        b = new Vec3D(x, y / setting.getPixel_per_mm() + setting.getBorder(), z);

                        x = modelWidth - (i * xLVL + setting.getBorder());
                        z = 0;
                        c = new Vec3D(x, y / setting.getPixel_per_mm() + setting.getBorder(), z);

                        x = modelWidth - ((i + 1) * xLVL + setting.getBorder());
                        z = 0;
                        d = new Vec3D(x, y / setting.getPixel_per_mm() + setting.getBorder(), z);

                        va = new Vertex(a, 0);
                        vb = new Vertex(b, 1);
                        vc = new Vertex(c, 2);
                        vd = new Vertex(d, 3);

                        mesh.addFace(va, vc, vd);
                        mesh.addFace(va, vb, vd);
                    }
                }

                for (int i = 0; i < original.getHeight() - 1; i++) {

                    for (x = 0; x <= original.getWidth() - 1; x += original.getWidth() - 1) {
                        y = i * yLVL + setting.getBorder();
                        z = quantizationLevel * heightLVL * heights[(int) x + i * original.getWidth()] + setting.getThinnest_layer();
                        a = new Vec3D(Math.abs(original.getWidth() - 1 - x) / setting.getPixel_per_mm() + setting.getBorder(), y, z);

                        y = (i + 1) * yLVL + setting.getBorder();
                        z = quantizationLevel * heightLVL * heights[(int) x + (i + 1) * original.getWidth()] + setting.getThinnest_layer();
                        b = new Vec3D(Math.abs(original.getWidth() - 1 - x) / setting.getPixel_per_mm() + setting.getBorder(), y, z);

                        y = i * yLVL + setting.getBorder();
                        z = 0;
                        c = new Vec3D(Math.abs(original.getWidth() - 1 - x) / setting.getPixel_per_mm() + setting.getBorder(), y, z);

                        y = (i + 1) * yLVL + setting.getBorder();
                        z = 0;
                        d = new Vec3D(Math.abs(original.getWidth() - 1 - x) / setting.getPixel_per_mm() + setting.getBorder(), y, z);

                        va = new Vertex(a, 0);
                        vb = new Vertex(b, 1);
                        vc = new Vertex(c, 2);
                        vd = new Vertex(d, 3);

                        mesh.addFace(va, vc, vd);
                        mesh.addFace(va, vb, vd);
                    }
                }
            } else {
                for (int i = 0; i < original.getWidth() - 1; i++) {
                    for (y = 0; y <= original.getHeight() - 1; y += original.getHeight() - 1) {
                        x = modelWidth - (i * xLVL + setting.getBorder());
                        z = quantizationLevel * heightLVL * heights[i + (int) y * original.getWidth()] + setting.getThinnest_layer();
                        a = new Vec3D(x, y / setting.getPixel_per_mm() + setting.getBorder(), z);

                        x = modelWidth - ((i + 1) * xLVL + setting.getBorder());
                        z = quantizationLevel * heightLVL * heights[i + 1 + (int) y * original.getWidth()] + setting.getThinnest_layer();
                        b = new Vec3D(x, y / setting.getPixel_per_mm() + setting.getBorder(), z);

                        x = modelWidth - (i * xLVL + setting.getBorder());
                        z = setting.getThickness();
                        c = new Vec3D(x, y / setting.getPixel_per_mm() + setting.getBorder(), z);

                        x = modelWidth - ((i + 1) * xLVL + setting.getBorder());
                        z = setting.getThickness();
                        d = new Vec3D(x, y / setting.getPixel_per_mm() + setting.getBorder(), z);

                        va = new Vertex(a, 0);
                        vb = new Vertex(b, 1);
                        vc = new Vertex(c, 2);
                        vd = new Vertex(d, 3);

                        mesh.addFace(va, vc, vd);
                        mesh.addFace(va, vb, vd);
                    }
                }

                for (int i = 0; i < original.getHeight() - 1; i++) {

                    for (x = 0; x <= original.getWidth() - 1; x += original.getWidth() - 1) {
                        y = i * yLVL + setting.getBorder();
                        z = quantizationLevel * heightLVL * heights[(int) x + i * original.getWidth()] + setting.getThinnest_layer();
                        a = new Vec3D(Math.abs(original.getWidth() - 1 - x) / setting.getPixel_per_mm() + setting.getBorder(), y, z);

                        y = (i + 1) * yLVL + setting.getBorder();
                        z = quantizationLevel * heightLVL * heights[(int) x + (i + 1) * original.getWidth()] + setting.getThinnest_layer();
                        b = new Vec3D(Math.abs(original.getWidth() - 1 - x) / setting.getPixel_per_mm() + setting.getBorder(), y, z);

                        y = i * yLVL + setting.getBorder();
                        z = setting.getThickness();
                        c = new Vec3D(Math.abs(original.getWidth() - 1 - x) / setting.getPixel_per_mm() + setting.getBorder(), y, z);

                        y = (i + 1) * yLVL + setting.getBorder();
                        z = setting.getThickness();
                        d = new Vec3D(Math.abs(original.getWidth() - 1 - x) / setting.getPixel_per_mm() + setting.getBorder(), y, z);

                        va = new Vertex(a, 0);
                        vb = new Vertex(b, 1);
                        vc = new Vertex(c, 2);
                        vd = new Vertex(d, 3);

                        mesh.addFace(va, vc, vd);
                        mesh.addFace(va, vb, vd);
                    }
                }
            }
            mProgressDialog.setProgress(80);
            //mesh optimization...
            mesh.rotateZ(3.14156f);
            mesh.computeFaceNormals();
            mesh.computeVertexNormals();
            mesh.faceOutwards();

            // create STLWriter instance
            STLWriter stl = new STLWriter();
            stl.beginSave(getFilesDir() + File.separator + "temp.stl", mesh.getNumFaces());

            int k=0;
            // iterate over all mesh faces
            for (Iterator i=mesh.faces.iterator(); i.hasNext();) {
                Face f=(Face)i.next();
                stl.face(f.b, f.a, f.c, f.normal, -1);
                k++;
            }
            stl.endSave();
            mProgressDialog.setProgress(100);
            process = 4;
            publishProgress();
            Intent intent = new Intent(MainActivity.this, ViewActivity.class);
            startActivity(intent);
            return null;
        }
        protected void onProgressUpdate(String... progress) {
            if (process == 1){
                mProgressDialog.setMessage("Scaling image...");
            } else if (process == 2){
                mProgressDialog.setMessage("Converting to Grayscale...");
            } else if (process == 3){
                mProgressDialog.setMessage("Constructing Model...");
            } else if (process == 4){
                mProgressDialog.setMessage("Done");
            }
        }

        @Override
        protected void onPostExecute(String unused) {
            mProgressDialog.dismiss();
            mProgressDialog = null;
        }
    }

    public void getPermissions() {
        // Here, thisActivity is the current activity
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {

            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.READ_EXTERNAL_STORAGE)) {

            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE);
            }
        }
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {

            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)) {

            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE);
            }
        }
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.SYSTEM_ALERT_WINDOW)
                != PackageManager.PERMISSION_GRANTED) {

            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.SYSTEM_ALERT_WINDOW)) {

            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.SYSTEM_ALERT_WINDOW},
                        MY_PERMISSIONS_REQUEST_SYSTEM_ALERT_WINDOW);
            }
        }
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress,
                                  boolean fromUser) {
        switch(seekBar.getId()) {
            case R.id.seekBarMaximumSize:{
                setting.setMax_size(seekBar.getProgress() + 50);
                tMaxSize.setText(String.valueOf(setting.getMax_size()) + "mm");
                Log.d("Maximum Size", String.valueOf(setting.getMax_size()));
                break;
            }
            case R.id.seekBarThickness: {
                setting.setThickness((seekBar.getProgress() + 30) / 10.0f);
                tThickness.setText(String.valueOf(setting.getThickness()) + "mm");
                Log.d("Thickness", String.valueOf(setting.getThickness()));
                break;
            }
            case R.id.seekBarBorder:{
                setting.setBorder(seekBar.getProgress());
                tBorder.setText(String.valueOf(setting.getBorder()) + "mm");
                Log.d("Border", String.valueOf(setting.getBorder()));
                break;
            }
            case R.id.seekBarThinnestLayer: {
                setting.setThinnest_layer((seekBar.getProgress() + 20) / 10.0f);
                tThinnestLayer.setText(String.valueOf(setting.getThinnest_layer()) + "mm");
                Log.d("Thinnest Layer", String.valueOf(setting.getThinnest_layer()));
                break;
            }
            case R.id.seekBarPixelPerMM:{
                setting.setPixel_per_mm(seekBar.getProgress() + 1);
                tPixelPerMM.setText(String.valueOf(setting.getPixel_per_mm()));
                Log.d("Pixel Per MM", String.valueOf(setting.getPixel_per_mm()));
                break;
            }
            default:
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            int newWidth = 0, newHeight = 0;
            try {
                imageRaw = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
                if (imageRaw.getWidth() > imageRaw.getHeight()) {
                    newWidth = 1000;
                    newHeight = (int)(imageRaw.getHeight()* 1000 / (float)imageRaw.getWidth());
                } else {
                    newWidth = (int)(imageRaw.getWidth()* 1000 / (float)imageRaw.getHeight());
                    newHeight = 1000;
                }
                imageSelect.setImageBitmap(scaleImage(imageRaw, newWidth, newHeight));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public Bitmap scaleImage(Bitmap original, int newWidth, int newHeight) {
        int[] rawInput = new int[original.getHeight() * original.getWidth()];
        original.getPixels(rawInput, 0, original.getWidth(), 0, 0, original.getWidth(), original.getHeight());

        int[] rawOutput = new int[newWidth * newHeight];

        // YD compensates for the x loop by subtracting the width back out
        int YD = (original.getHeight() / newHeight) * original.getWidth() - original.getWidth();
        int YR = original.getHeight() % newHeight;
        int XD = original.getWidth() / newWidth;
        int XR = original.getWidth() % newWidth;
        int outOffset = 0;
        int inOffset =  0;

        for (int y = newHeight, YE = 0; y > 0; y--) {
            for (int x = newWidth, XE = 0; x > 0; x--) {
                rawOutput[outOffset++]= rawInput[inOffset];
                inOffset += XD;
                XE += XR;
                if (XE >= newWidth) {
                    XE -= newWidth;
                    inOffset++;
                }
            }
            inOffset += YD;
            YE += YR;
            if (YE >= newHeight) {
                YE -= newHeight;
                inOffset += original.getWidth();
            }
        }
        return Bitmap.createBitmap(rawOutput, 0, newWidth, newWidth, newHeight, Bitmap.Config.ARGB_8888);
    }

    public Bitmap grayscaleImage(Bitmap original, boolean positive) {
        int[] rawInput = new int[original.getHeight() * original.getWidth()];
        original.getPixels(rawInput, 0, original.getWidth(), 0, 0, original.getWidth(), original.getHeight());

        int[] rawOutput = new int[original.getHeight() * original.getWidth()];

        for (int i = 0; i < rawInput.length; i++) {
            if(positive) {
                rawOutput[i] = Color.rgb((int)(255.0f - (0.299f * Color.red(rawInput[i]) + 0.587f * Color.green(rawInput[i])
                        + 0.114f * Color.blue(rawInput[i]))), (int)(255.0f - (0.299f * Color.red(rawInput[i]) + 0.587f * Color.green(rawInput[i])
                        + 0.114f * Color.blue(rawInput[i]))), (int)(255.0f - (0.299f * Color.red(rawInput[i]) + 0.587f * Color.green(rawInput[i])
                        + 0.114f * Color.blue(rawInput[i]))));
            } else {
                rawOutput[i] = Color.rgb((int)(0.299f * Color.red(rawInput[i]) + 0.587f * Color.green(rawInput[i])
                        + 0.114f * Color.blue(rawInput[i])), (int)(0.299f * Color.red(rawInput[i]) + 0.587f * Color.green(rawInput[i])
                        + 0.114f * Color.blue(rawInput[i])), (int)(0.299f * Color.red(rawInput[i]) + 0.587f * Color.green(rawInput[i])
                        + 0.114f * Color.blue(rawInput[i])));
            }
        }
        return Bitmap.createBitmap(rawOutput, 0, original.getWidth(), original.getWidth(), original.getHeight(), Bitmap.Config.ARGB_8888);
    }

}
