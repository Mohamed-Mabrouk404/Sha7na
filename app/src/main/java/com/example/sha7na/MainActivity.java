package com.example.sha7na;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.File;
import java.util.List;
import java.util.Map;




public class MainActivity extends AppCompatActivity {
    private static final int REQ_CAMERA = 1;
    private static final int REQ_PHONE_STATE = 2;
    private static final int REQ_CALL_PHONE = 3;

    private Uri photoUri;
    private TextRecognizer recognizer;

    // pending action
    private enum PendingAction { NONE, OPEN_CAMERA, CHOOSE_SIM, MAKE_CALL }

    private PendingAction pendingAction = PendingAction.NONE;

    private static final Map<String, String> SimPrefixCode = Map.ofEntries(
            Map.entry("vodafone", "*858*"),
            Map.entry("we", "*555*"),
            Map.entry("orange", "#102*"),
            Map.entry("etisalat", "*556*")
    );

    private String pendingUssdCode = null;
    private int pendingSubscriptionId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        Button scanBtn = findViewById(R.id.scan_btn);
        scanBtn.setOnClickListener(v -> openCamera());
    }

    // ================= Helper =================
    private View getCustomDialogTitle(Context context, String titleText) {
        LayoutInflater inflater = LayoutInflater.from(context);
        View customTitle = inflater.inflate(R.layout.dialog_title_rtl, null);

        TextView titleView = customTitle.findViewById(R.id.dialog_title);
        titleView.setText(titleText);

        return customTitle;
    }
    // ================= CAMERA =================
    public void openCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            pendingAction = PendingAction.OPEN_CAMERA;
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
            return;
        }

        File photoFile = new File(getExternalFilesDir(null), "captured.jpg");
        photoUri = FileProvider.getUriForFile(this,
                getPackageName() + ".provider", photoFile);

        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
        startActivityForResult(intent, REQ_CAMERA);
    }

    // ================= SIM SELECTION =================
    private void chooseSimAndDial(String code) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED) {
            pendingAction = PendingAction.CHOOSE_SIM;
            pendingUssdCode = code;
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_PHONE_STATE}, REQ_PHONE_STATE);
            return;
        }

        SubscriptionManager subscriptionManager =
                (SubscriptionManager) getSystemService(TELEPHONY_SUBSCRIPTION_SERVICE);

        List<SubscriptionInfo> subscriptionInfos = subscriptionManager.getActiveSubscriptionInfoList();
        if (subscriptionInfos == null || subscriptionInfos.isEmpty()) {
            Toast.makeText(this, "No SIM cards found", Toast.LENGTH_SHORT).show();
            return;
        }


        SimAdapter adapter = new SimAdapter(this, subscriptionInfos);

        runOnUiThread(() -> {
            AlertDialog simsDialog = new AlertDialog
                    .Builder(this, R.style.MyAlertDialogTheme)
                    .setCustomTitle(getCustomDialogTitle(this, "إختر الشريحة"))
                    .setAdapter(adapter, (d, which) -> {
                        SubscriptionInfo chosenSim = subscriptionInfos.get(which);

                        String prefixCode = SimPrefixCode.get(chosenSim.getCarrierName().toString().toLowerCase());
                        String ussdCode = prefixCode + code + "#";

                        // ⚠️ Show confirmation before making the call
                        AlertDialog cautionDialog = new AlertDialog
                                .Builder(this, R.style.MyAlertDialogTheme)
                                .setCustomTitle(getCustomDialogTitle(this, "تنبيه"))
                                .setMessage("سيتم شحن الكارت كرصيد إذا كان الكارت يمكن شحنه كرصيد عادي ووحدات." +
                                        " \n\n" +
                                        "سيتم شحن الكارت كوحدات إذا كان الكارت من نوع (فكة).")
                                .setPositiveButton("موافق", (confirmDialog, w1) -> {
                                    makeDirectCall(ussdCode, chosenSim.getSubscriptionId());
                                })
                                .setNegativeButton("غير موافق", (confirmDialog, w1) -> {
                                    confirmDialog.dismiss();
                                }).create();

                        cautionDialog.setOnShowListener(dialog -> {
                            Window window = cautionDialog.getWindow();
                            if (window != null) {
                                View decorView = window.getDecorView();

                                // ✅ أجبر الاتجاه RTL
                                decorView.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
                                decorView.setTextDirection(View.TEXT_DIRECTION_RTL);
                            }
                        });

                        cautionDialog.show();

                        if (cautionDialog.getWindow() != null) {

                            cautionDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

                            Window window = cautionDialog.getWindow();

                            WindowManager.LayoutParams params = window.getAttributes();
                            params.gravity = Gravity.BOTTOM; // move to bottom
                            params.y = 20; // margin from bottom (in px)
                            window.setAttributes(params);
                        }

//                        makeDirectCall(ussdCode, chosenSim.getSubscriptionId());
                    })
                    .setNegativeButton("إغلاق", (d, w) -> d.dismiss())
                    .create();

            simsDialog.show();

            // Style the list
            ListView listView = simsDialog.getListView();
            if (listView != null) {
                listView.setDivider(new ColorDrawable(Color.TRANSPARENT));
                listView.setDividerHeight(20);
                listView.setPadding(12, 12, 12, 12);
            }

            // Position at bottom
            if (simsDialog.getWindow() != null) {
                simsDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                simsDialog.getWindow().setBackgroundDrawable(ContextCompat.getDrawable(this, R.drawable.dialog_background));

                Window window = simsDialog.getWindow();
                window.setLayout((int) (getResources().getDisplayMetrics().widthPixels * 0.95), // almost full width
                        WindowManager.LayoutParams.WRAP_CONTENT);

                WindowManager.LayoutParams params = window.getAttributes();
                params.gravity = Gravity.BOTTOM; // move to bottom
                params.y = 20; // margin from bottom (in px)
                window.setAttributes(params);
            }
        });
    }

    // ================= MAKE CALL =================
    @Nullable
    private PhoneAccountHandle getPhoneAccountHandle(int subscriptionId) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_PHONE_STATE}, 201);
            return null;
        }

        SubscriptionManager subscriptionManager = SubscriptionManager.from(this);
        List<SubscriptionInfo> subs = subscriptionManager.getActiveSubscriptionInfoList();

        if (subs != null) {
            for (SubscriptionInfo info : subs) {
                if (info.getSubscriptionId() == subscriptionId) {
                    TelecomManager telecomManager = (TelecomManager) getSystemService(TELECOM_SERVICE);
                    if (telecomManager != null) {
                        for (PhoneAccountHandle handle : telecomManager.getCallCapablePhoneAccounts()) {
                            if (handle.getId().contains(String.valueOf(subscriptionId))) {
                                return handle;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }
    private void makeDirectCall(String ussdCode, int subscriptionId) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
                != PackageManager.PERMISSION_GRANTED) {
            pendingAction = PendingAction.MAKE_CALL;
            pendingUssdCode = ussdCode;
            pendingSubscriptionId = subscriptionId;
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CALL_PHONE}, REQ_CALL_PHONE);
            return;
        }

        try {
            TelecomManager telecomManager = (TelecomManager) getSystemService(TELECOM_SERVICE);
            if (telecomManager != null) {
                Bundle extras = new Bundle();
                extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE,
                        getPhoneAccountHandle(subscriptionId));

                Uri uri = Uri.fromParts("tel", ussdCode, null);
                telecomManager.placeCall(uri, extras);
            }
        } catch (SecurityException e) {
            e.printStackTrace();
            Toast.makeText(this, "Permission denied to place call", Toast.LENGTH_SHORT).show();
        }
    }

    // ================= PERMISSION HANDLER =================
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (grantResults.length == 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show();
            pendingAction = PendingAction.NONE;
            return;
        }

        switch (requestCode) {
            case REQ_CAMERA:
                if (pendingAction == PendingAction.OPEN_CAMERA) {
                    pendingAction = PendingAction.NONE;
                    openCamera(); // resume
                }
                break;

            case REQ_PHONE_STATE:
                if (pendingAction == PendingAction.CHOOSE_SIM) {
                    pendingAction = PendingAction.NONE;
                    chooseSimAndDial(pendingUssdCode);
                    pendingUssdCode = null;
                }
                break;

            case REQ_CALL_PHONE:
                if (pendingAction == PendingAction.MAKE_CALL) {
                    pendingAction = PendingAction.NONE;
                    makeDirectCall(pendingUssdCode, pendingSubscriptionId);
                    pendingUssdCode = null;
                    pendingSubscriptionId = -1;
                }
                break;
        }
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQ_CAMERA && resultCode == RESULT_OK) {
            try {
                Bitmap bitmap = BitmapFactory.decodeStream(getContentResolver().openInputStream(photoUri));

                // Run OCR
                InputImage image = InputImage.fromBitmap(bitmap, 0);
                recognizer.process(image)
                        .addOnSuccessListener(visionText -> {
                            String rawText = visionText.getText();

                            // Find 16 digits in a row
                            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\b\\d{16}\\b");
                            java.util.regex.Matcher matcher = pattern.matcher(rawText);

                            if (matcher.find()) {
                                String code = matcher.group();
                                chooseSimAndDial(code);   // 🔥 this shows your SIM selection dialog
                            } else {
                                Toast.makeText(this, "No 16-digit code found", Toast.LENGTH_SHORT).show();
                            }
                        })
                        .addOnFailureListener(e ->
                                Toast.makeText(this, "OCR Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                        );

            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "Error loading image", Toast.LENGTH_SHORT).show();
            }
        }
    }

}










//
//
//public class MainActivity extends AppCompatActivity {
//    private static final int CAMERA_IMAGE_CODE = 2;
//    private static final int REQUEST_CAMERA_PERMISSION_CODE = 1;
//    private Uri photoUri;   // will hold the captured image uri
//    private TextRecognizer recognizer; // ML Kit recognizer
//    private String pendingUssdCode = null;
//    private int pendingSubscriptionId = -1;
//
//    private static final Map<String, String> SimPrefixCode = Map.ofEntries(
//            Map.entry("vodafone", "*858*"),
//            Map.entry("we", "*555*"),
//            Map.entry("orange", "#102*"),
//            Map.entry("etisalat", "*556*")
//    );
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        EdgeToEdge.enable(this);
//        setContentView(R.layout.activity_main);
//
//        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
//            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
//            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
//            return insets;
//        });
//
//        Button scanBtn = findViewById(R.id.scan_btn);
//
//        // Initialize ML Kit recognizer (Latin only, works offline)
//        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
//
//        scanBtn.setOnClickListener(v -> openCamera());
////        openCamera();
//    }
//
//    public void openCamera() {
//        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
//            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION_CODE);
//            return;
//        }
//
//        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
//
//        // Create a file to store the captured image
//        File photoFile = new File(getExternalFilesDir(null), "captured.jpg");
//        photoUri = FileProvider.getUriForFile(this, getPackageName() + ".provider", photoFile);
//
//        intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
//        startActivityForResult(intent, CAMERA_IMAGE_CODE);
//    }
//
//    @Override
//    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
//        super.onActivityResult(requestCode, resultCode, data);
//
//        if (requestCode == CAMERA_IMAGE_CODE && resultCode == RESULT_OK) {
//            try {
//                Bitmap bitmap = BitmapFactory.decodeStream(getContentResolver().openInputStream(photoUri));
//
//                // Run OCR with ML Kit
//                runTextRecognition(bitmap);
//            } catch (Exception e) {
//                e.getMessage();
//                Toast.makeText(this, "Error loading image", Toast.LENGTH_SHORT).show();
//            }
//        }
//    }
//
//    private void runTextRecognition(Bitmap bitmap) {
//        InputImage image = InputImage.fromBitmap(bitmap, 0);
//
//        recognizer.process(image)
//                .addOnSuccessListener(this::processText)
//                .addOnFailureListener(
//                e -> Toast.makeText(this, "OCR Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show()
//                );
//    }
//
//    private void processText(Text visionText) {
//        String rawText = visionText.getText();
//
//        // Regex to find exactly 16 digits in a row
//        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\b\\d{16}\\b");
//        java.util.regex.Matcher matcher = pattern.matcher(rawText);
//
//        if (matcher.find()) {
//            String code = matcher.group();
////            Toast.makeText(this, "Extracted Code: " + code, Toast.LENGTH_LONG).show();
//
//            chooseSimAndDial(code);
//        } else {
//            Toast.makeText(this, "No 16-digit code found", Toast.LENGTH_SHORT).show();
//        }
//    }
//
//    private View getCustomDialogTitle(Context context, String titleText) {
//        LayoutInflater inflater = LayoutInflater.from(context);
//        View customTitle = inflater.inflate(R.layout.dialog_title_rtl, null);
//
//        TextView titleView = customTitle.findViewById(R.id.dialog_title);
//        titleView.setText(titleText);
//
//        return customTitle;
//    }
//
//    private void chooseSimAndDial(String code) {
//        SubscriptionManager subscriptionManager = (SubscriptionManager) getSystemService(TELEPHONY_SUBSCRIPTION_SERVICE);
//
//        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
//            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_PHONE_STATE}, 101);
//            return;
//        }
//
//        List<SubscriptionInfo> subscriptionInfos = subscriptionManager.getActiveSubscriptionInfoList();
//
//        if (subscriptionInfos == null || subscriptionInfos.isEmpty()) {
//            Toast.makeText(this, "No SIM cards found", Toast.LENGTH_SHORT).show();
//            return;
//        }
//
//        SimAdapter adapter = new SimAdapter(this, subscriptionInfos);
//
//        runOnUiThread(() -> {
//            AlertDialog simsDialog = new AlertDialog
//                    .Builder(this, R.style.MyAlertDialogTheme)
//                    .setCustomTitle(getCustomDialogTitle(this, "إختر الشريحة"))
//                    .setAdapter(adapter, (d, which) -> {
//                        SubscriptionInfo chosenSim = subscriptionInfos.get(which);
//
//                        String prefixCode = SimPrefixCode.get(chosenSim.getCarrierName().toString().toLowerCase());
//                        String ussdCode = prefixCode + code + "#";
//
//                        // ⚠️ Show confirmation before making the call
//                        AlertDialog cautionDialog = new AlertDialog
//                                .Builder(this, R.style.MyAlertDialogTheme)
//                                .setCustomTitle(getCustomDialogTitle(this, "تنبيه"))
//                                .setMessage("سيتم شحن الكارت كرصيد إذا كان الكارت يمكن شحنه كرصيد عادي ووحدات." +
//                                        " \n\n" +
//                                        "سيتم شحن الكارت كوحدات إذا كان الكارت من نوع (فكة).")
//                                .setPositiveButton("موافق", (confirmDialog, w1) -> {
//                                    makeDirectCall(ussdCode, chosenSim.getSubscriptionId());
//                                })
//                                .setNegativeButton("غير موافق", (confirmDialog, w1) -> {
//                                    confirmDialog.dismiss();
//                                }).create();
//
//                        cautionDialog.setOnShowListener(dialog -> {
//                            Window window = cautionDialog.getWindow();
//                            if (window != null) {
//                                View decorView = window.getDecorView();
//
//                                // ✅ أجبر الاتجاه RTL
//                                decorView.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
//                                decorView.setTextDirection(View.TEXT_DIRECTION_RTL);
//                            }
//                        });
//
//                        cautionDialog.show();
//
//                        if (cautionDialog.getWindow() != null) {
//
//                            cautionDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
//
//                            Window window = cautionDialog.getWindow();
//
//                            WindowManager.LayoutParams params = window.getAttributes();
//                            params.gravity = Gravity.BOTTOM; // move to bottom
//                            params.y = 20; // margin from bottom (in px)
//                            window.setAttributes(params);
//                        }
//
////                        makeDirectCall(ussdCode, chosenSim.getSubscriptionId());
//                    })
//                    .setNegativeButton("إغلاق", (d, w) -> d.dismiss())
//                    .create();
//
//            simsDialog.show();
//
//            // Style the list
//            ListView listView = simsDialog.getListView();
//            if (listView != null) {
//                listView.setDivider(new ColorDrawable(Color.TRANSPARENT));
//                listView.setDividerHeight(20);
//                listView.setPadding(12, 12, 12, 12);
//            }
//
//            // Position at bottom
//            if (simsDialog.getWindow() != null) {
//                simsDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
//                simsDialog.getWindow().setBackgroundDrawable(ContextCompat.getDrawable(this, R.drawable.dialog_background));
//
//                Window window = simsDialog.getWindow();
//                window.setLayout((int) (getResources().getDisplayMetrics().widthPixels * 0.95), // almost full width
//                        WindowManager.LayoutParams.WRAP_CONTENT);
//
//                WindowManager.LayoutParams params = window.getAttributes();
//                params.gravity = Gravity.BOTTOM; // move to bottom
//                params.y = 20; // margin from bottom (in px)
//                window.setAttributes(params);
//            }
//        });
//    }
//
//    private void makeDirectCall(String ussdCode, int subscriptionId) {
//        // Check CALL_PHONE permission
//        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
//            // Request permission if not granted
//            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CALL_PHONE}, 102);
//            return; // exit, wait for user response
//        }
//
//        try {
//            TelecomManager telecomManager = (TelecomManager) getSystemService(TELECOM_SERVICE);
//            if (telecomManager != null) {
//                Bundle extras = new Bundle();
//                extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, getPhoneAccountHandle(subscriptionId));
//
//                Uri uri = Uri.fromParts("tel", ussdCode, null);
//                telecomManager.placeCall(uri, extras);
//            }
//        } catch (SecurityException e) {
//            e.printStackTrace();
//            Toast.makeText(this, "Permission denied to place call", Toast.LENGTH_SHORT).show();
//        }
//    }
//
//    @Nullable
//    private PhoneAccountHandle getPhoneAccountHandle(int subscriptionId) {
//        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
//            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_PHONE_STATE}, 201);
//            return null;
//        }
//
//        SubscriptionManager subscriptionManager = SubscriptionManager.from(this);
//        List<SubscriptionInfo> subs = subscriptionManager.getActiveSubscriptionInfoList();
//
//        if (subs != null) {
//            for (SubscriptionInfo info : subs) {
//                if (info.getSubscriptionId() == subscriptionId) {
//                    TelecomManager telecomManager = (TelecomManager) getSystemService(TELECOM_SERVICE);
//                    if (telecomManager != null) {
//                        for (PhoneAccountHandle handle : telecomManager.getCallCapablePhoneAccounts()) {
//                            if (handle.getId().contains(String.valueOf(subscriptionId))) {
//                                return handle;
//                            }
//                        }
//                    }
//                }
//            }
//        }
//        return null;
//    }
//
//    @Override
//    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
//        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
//
//        if (requestCode == 102) {
//            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
////                Toast.makeText(this, "Permission granted, try again", Toast.LENGTH_SHORT).show();
//
//                makeDirectCall(pendingUssdCode, pendingSubscriptionId);
//                pendingUssdCode = null;
//                pendingSubscriptionId = -1;
//            } else {
//                Toast.makeText(this, "Permission required to make calls", Toast.LENGTH_SHORT).show();
//            }
//        }
//    }
//}