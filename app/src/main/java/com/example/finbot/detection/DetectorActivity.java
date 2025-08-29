package com.example.finbot.detection;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.util.Size;
import android.util.TypedValue;
import android.view.View;
import android.widget.Toast;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.example.finbot.R;
import com.example.finbot.detection.customview.OverlayView;
import com.example.finbot.detection.customview.OverlayView.DrawCallback;
import com.example.finbot.detection.env.BorderedText;
import com.example.finbot.detection.env.ImageUtils;
import com.example.finbot.detection.env.Logger;
import com.example.finbot.detection.tflite.Classifier;
import com.example.finbot.detection.tflite.DetectorFactory;
import com.example.finbot.detection.tflite.YoloV5Classifier;
import com.example.finbot.detection.tracking.MultiBoxTracker;

/**
 * An activity that uses a TensorFlowMultiBoxDetector and ObjectTracker to detect and then track
 * objects.
 */
public class DetectorActivity extends CameraActivity implements OnImageAvailableListener {
    private static final Logger LOGGER = new Logger();

    private static final DetectorMode MODE = DetectorMode.TF_OD_API;
    private static final float MINIMUM_CONFIDENCE_TF_OD_API = 0.3f;
    private static final boolean MAINTAIN_ASPECT = true;
    private static final Size DESIRED_PREVIEW_SIZE = new Size(640, 640);
    private static final boolean SAVE_PREVIEW_BITMAP = false;
    private static final float TEXT_SIZE_DIP = 10;

    OverlayView trackingOverlay;
    private Integer sensorOrientation;

    private YoloV5Classifier detector;
    private TextRecognizer textRecognizer;

    private long lastProcessingTimeMs;
    private Bitmap rgbFrameBitmap = null;
    private Bitmap croppedBitmap = null;
    private Bitmap cropCopyBitmap = null;
    private Bitmap capturedBitmap = null; // Store captured image for OCR

    private boolean computingDetection = false;
    private boolean isCapturing = false;

    private long timestamp = 0;

    private Matrix frameToCropTransform;
    private Matrix cropToFrameTransform;

    private MultiBoxTracker tracker;
    private BorderedText borderedText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize ML Kit Text Recognition
        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        captureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                captureAndProcessImage();
            }
        });
    }

    private void captureAndProcessImage() {
        if (rgbFrameBitmap == null) {
            Toast.makeText(this, "Camera not ready", Toast.LENGTH_SHORT).show();
            return;
        }

        isCapturing = true;
        captureButton.setEnabled(false);
        captureButton.setText("Processing...");

        // Capture current frame
        capturedBitmap = Bitmap.createBitmap(rgbFrameBitmap);

        // Process the captured image
        runInBackground(new Runnable() {
            @Override
            public void run() {
                processCapturedImage();
            }
        });
    }

    private void processCapturedImage() {
        try {
            // 1. First run object detection on the captured image
            final Canvas canvas = new Canvas(croppedBitmap);
            canvas.drawBitmap(capturedBitmap, frameToCropTransform, null);

            final List<Classifier.Recognition> detectionResults = detector.recognizeImage(croppedBitmap);

            // 2. Find regions of interest (ROIs) from object detection
            List<RectF> textRegions = extractTextRegions(detectionResults);

            // 3. Perform OCR on the entire image and filtered regions
            performOCRAnalysis(capturedBitmap, textRegions);

        } catch (Exception e) {
            LOGGER.e(e, "Error processing captured image");
            runOnUiThread(() -> {
                Toast.makeText(DetectorActivity.this, "Error processing image", Toast.LENGTH_SHORT).show();
                resetCaptureButton();
            });
        }
    }

    private List<RectF> extractTextRegions(List<Classifier.Recognition> detectionResults) {
        List<RectF> textRegions = new ArrayList<>();

        // Filter detections for text-relevant objects (receipts, documents, labels, etc.)
        for (Classifier.Recognition result : detectionResults) {
            if (result.getConfidence() >= MINIMUM_CONFIDENCE_TF_OD_API) {
                String detectedClass = result.getTitle().toLowerCase();

                // Add logic to identify text-containing objects
                // This depends on your YOLO model's classes
                if (isTextRelevantClass(detectedClass)) {
                    RectF location = result.getLocation();
                    if (location != null) {
                        // Map back to original image coordinates
                        RectF originalLocation = new RectF(location);
                        cropToFrameTransform.mapRect(originalLocation);
                        textRegions.add(originalLocation);
                    }
                }
            }
        }

        return textRegions;
    }

    private boolean isTextRelevantClass(String detectedClass) {
        // Based on your YOLO classes that contain text information
        return detectedClass.toLowerCase().contains("address") ||
                detectedClass.toLowerCase().contains("date") ||
                detectedClass.toLowerCase().contains("item") ||
                detectedClass.toLowerCase().contains("orderid") ||
                detectedClass.toLowerCase().contains("subtotal") ||
                detectedClass.toLowerCase().contains("tax") ||
                detectedClass.toLowerCase().contains("title") ||
                detectedClass.toLowerCase().contains("totalprice");
    }

    private void performOCRAnalysis(Bitmap bitmap, List<RectF> textRegions) {
        InputImage image = InputImage.fromBitmap(bitmap, 0);

        textRecognizer.process(image)
                .addOnSuccessListener(visionText -> {
                    processOCRResults(visionText, textRegions);
                })
                .addOnFailureListener(e -> {
                    LOGGER.e(e, "OCR processing failed");
                    runOnUiThread(() -> {
                        Toast.makeText(DetectorActivity.this, "OCR processing failed", Toast.LENGTH_SHORT).show();
                        resetCaptureButton();
                    });
                });
    }

    private void processOCRResults(Text visionText, List<RectF> textRegions) {
        StringBuilder allText = new StringBuilder();

        // Initialize extracted fields
        String extractedAddress = "";
        String extractedDate = "";
        String extractedItem = "";
        String extractedOrderId = "";
        String extractedSubtotal = "";
        String extractedTax = "";
        String extractedTitle = "";
        String extractedTotalPrice = "";

        // Process all detected text blocks
        for (Text.TextBlock block : visionText.getTextBlocks()) {
            String blockText = block.getText();
            allText.append(blockText).append("\n");

            RectF blockRect = new RectF(block.getBoundingBox());

            // Check if this text block is within any of our ROIs or process all text
            if (textRegions.isEmpty() || isWithinTextRegions(blockRect, textRegions)) {
                // Extract specific information using regex patterns
                if (extractedAddress.isEmpty()) extractedAddress = extractAddress(blockText);
                if (extractedDate.isEmpty()) extractedDate = extractDate(blockText);
                if (extractedItem.isEmpty()) extractedItem = extractItem(blockText);
                if (extractedOrderId.isEmpty()) extractedOrderId = extractOrderId(blockText);
                if (extractedSubtotal.isEmpty()) extractedSubtotal = extractSubtotal(blockText);
                if (extractedTax.isEmpty()) extractedTax = extractTax(blockText);
                if (extractedTitle.isEmpty()) extractedTitle = extractTitle(blockText);
                if (extractedTotalPrice.isEmpty()) extractedTotalPrice = extractTotalPrice(blockText);
            }
        }

        // If specific extraction failed, try on the complete text
        String completeText = allText.toString();
        if (extractedAddress.isEmpty()) extractedAddress = extractAddress(completeText);
        if (extractedDate.isEmpty()) extractedDate = extractDate(completeText);
        if (extractedItem.isEmpty()) extractedItem = extractItem(completeText);
        if (extractedOrderId.isEmpty()) extractedOrderId = extractOrderId(completeText);
        if (extractedSubtotal.isEmpty()) extractedSubtotal = extractSubtotal(completeText);
        if (extractedTax.isEmpty()) extractedTax = extractTax(completeText);
        if (extractedTitle.isEmpty()) extractedTitle = extractTitle(completeText);
        if (extractedTotalPrice.isEmpty()) extractedTotalPrice = extractTotalPrice(completeText);

        // Return results
        String finalExtractedAddress = extractedAddress;
        String finalExtractedDate = extractedDate;
        String finalExtractedItem = extractedItem;
        String finalExtractedOrderId = extractedOrderId;
        String finalExtractedSubtotal = extractedSubtotal;
        String finalExtractedTax = extractedTax;
        String finalExtractedTitle = extractedTitle;
        String finalExtractedTotalPrice = extractedTotalPrice;
        runOnUiThread(() -> returnOCRResults(finalExtractedAddress, finalExtractedDate, finalExtractedItem,
                finalExtractedOrderId, finalExtractedSubtotal, finalExtractedTax, finalExtractedTitle,
                finalExtractedTotalPrice, completeText));
    }

    private boolean isWithinTextRegions(RectF textRect, List<RectF> textRegions) {
        for (RectF region : textRegions) {
            if (RectF.intersects(textRect, region)) {
                return true;
            }
        }
        return false;
    }

    private String extractAddress(String text) {
        // Patterns for extracting addresses
        Pattern[] addressPatterns = {
                Pattern.compile("(?i)address[:\\s]*([\\w\\s,.-]+?)(?=\\n|$|phone|tel|email|zip)", Pattern.DOTALL),
                Pattern.compile("(?i)ship\\s*to[:\\s]*([\\w\\s,.-]+?)(?=\\n|$|phone|tel|email)", Pattern.DOTALL),
                Pattern.compile("(?i)billing[:\\s]*address[:\\s]*([\\w\\s,.-]+?)(?=\\n|$|phone|tel)", Pattern.DOTALL),
                Pattern.compile("(\\d+\\s+[\\w\\s]+(?:street|st|avenue|ave|road|rd|drive|dr|lane|ln|blvd|boulevard)[\\w\\s,]*)", Pattern.CASE_INSENSITIVE),
        };

        for (Pattern pattern : addressPatterns) {
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                return matcher.group(1).trim().replaceAll("\\s+", " ");
            }
        }
        return "";
    }

    private String extractDate(String text) {
        // Patterns for extracting dates
        Pattern[] datePatterns = {
                Pattern.compile("(?i)date[:\\s]*(\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4})"),
                Pattern.compile("(?i)order\\s*date[:\\s]*(\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4})"),
                Pattern.compile("(\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4})"), // MM/DD/YYYY or MM-DD-YYYY
                Pattern.compile("(\\d{2,4}[/-]\\d{1,2}[/-]\\d{1,2})"), // YYYY/MM/DD or YYYY-MM-DD
                Pattern.compile("(?i)(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*[\\s,]+\\d{1,2}[\\s,]+\\d{2,4}"),
                Pattern.compile("\\d{1,2}\\s+(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\\s+\\d{2,4}", Pattern.CASE_INSENSITIVE),
        };

        for (Pattern pattern : datePatterns) {
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                return matcher.group(matcher.groupCount()).trim();
            }
        }
        return "";
    }

    private String extractItem(String text) {
        // Patterns for extracting item names
        Pattern[] itemPatterns = {
                Pattern.compile("(?i)item[:\\s]*([\\w\\s.-]+?)(?=\\n|qty|quantity|price|\\$)", Pattern.DOTALL),
                Pattern.compile("(?i)product[:\\s]*([\\w\\s.-]+?)(?=\\n|qty|quantity|price|\\$)", Pattern.DOTALL),
                Pattern.compile("(?i)description[:\\s]*([\\w\\s.-]+?)(?=\\n|qty|quantity|price|\\$)", Pattern.DOTALL),
                // Look for lines that might be item descriptions (common pattern: text followed by price)
                Pattern.compile("([A-Za-z][\\w\\s.-]{3,})\\s+\\$?\\d+\\.\\d{2}"),
        };

        for (Pattern pattern : itemPatterns) {
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                return matcher.group(1).trim().replaceAll("\\s+", " ");
            }
        }
        return "";
    }

    private String extractOrderId(String text) {
        // Patterns for extracting order IDs
        Pattern[] orderIdPatterns = {
                Pattern.compile("(?i)order[\\s#]*id[:\\s#]*([A-Za-z0-9-]+)"),
                Pattern.compile("(?i)order[\\s#]*number[:\\s#]*([A-Za-z0-9-]+)"),
                Pattern.compile("(?i)receipt[\\s#]*number[:\\s#]*([A-Za-z0-9-]+)"),
                Pattern.compile("(?i)transaction[\\s#]*id[:\\s#]*([A-Za-z0-9-]+)"),
                Pattern.compile("#([A-Za-z0-9-]{6,})"), // Generic # followed by alphanumeric
        };

        for (Pattern pattern : orderIdPatterns) {
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
        }
        return "";
    }

    private String extractSubtotal(String text) {
        // Patterns for extracting subtotal
        Pattern[] subtotalPatterns = {
                Pattern.compile("(?i)subtotal[:\\s]*\\$?\\s*([0-9,]+\\.?[0-9]*)"),
                Pattern.compile("(?i)sub[\\s-]*total[:\\s]*\\$?\\s*([0-9,]+\\.?[0-9]*)"),
                Pattern.compile("(?i)before\\s*tax[:\\s]*\\$?\\s*([0-9,]+\\.?[0-9]*)"),
        };

        for (Pattern pattern : subtotalPatterns) {
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                return "$" + matcher.group(1).trim();
            }
        }
        return "";
    }

    private String extractTax(String text) {
        // Patterns for extracting tax
        Pattern[] taxPatterns = {
                Pattern.compile("(?i)tax[:\\s]*\\$?\\s*([0-9,]+\\.?[0-9]*)"),
                Pattern.compile("(?i)sales\\s*tax[:\\s]*\\$?\\s*([0-9,]+\\.?[0-9]*)"),
                Pattern.compile("(?i)vat[:\\s]*\\$?\\s*([0-9,]+\\.?[0-9]*)"),
                Pattern.compile("(?i)gst[:\\s]*\\$?\\s*([0-9,]+\\.?[0-9]*)"),
        };

        for (Pattern pattern : taxPatterns) {
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                return "$" + matcher.group(1).trim();
            }
        }
        return "";
    }

    private String extractTitle(String text) {
        // Patterns for extracting titles (business name, receipt title, etc.)
        Pattern[] titlePatterns = {
                Pattern.compile("(?i)title[:\\s]*([\\w\\s.-]+?)(?=\\n|address|phone|date)"),
                Pattern.compile("(?i)business[\\s]*name[:\\s]*([\\w\\s.-]+?)(?=\\n|address|phone)"),
                Pattern.compile("(?i)company[:\\s]*([\\w\\s.-]+?)(?=\\n|address|phone)"),
                // First line that's likely a business name (all caps or title case)
                Pattern.compile("^([A-Z][A-Z\\s&.-]{3,})$", Pattern.MULTILINE),
        };

        for (Pattern pattern : titlePatterns) {
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                return matcher.group(1).trim().replaceAll("\\s+", " ");
            }
        }
        return "";
    }

    private String extractTotalPrice(String text) {
        // Patterns for extracting total price
        Pattern[] totalPatterns = {
                Pattern.compile("(?i)total[:\\s]*\\$?\\s*([0-9,]+\\.?[0-9]*)"),
                Pattern.compile("(?i)grand[\\s]*total[:\\s]*\\$?\\s*([0-9,]+\\.?[0-9]*)"),
                Pattern.compile("(?i)amount[\\s]*due[:\\s]*\\$?\\s*([0-9,]+\\.?[0-9]*)"),
                Pattern.compile("(?i)final[\\s]*total[:\\s]*\\$?\\s*([0-9,]+\\.?[0-9]*)"),
        };

        for (Pattern pattern : totalPatterns) {
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                return "$" + matcher.group(1).trim();
            }
        }
        return "";
    }

    private void returnOCRResults(String address, String date, String item, String orderId,
                                  String subtotal, String tax, String title, String totalPrice, String fullText) {
        // Log the results
        LOGGER.i("OCR Results - Address: %s, Date: %s, Item: %s, OrderId: %s, Subtotal: %s, Tax: %s, Title: %s, TotalPrice: %s",
                address, date, item, orderId, subtotal, tax, title, totalPrice);

        Intent resultIntent = new Intent();
        resultIntent.putExtra("address", address.isEmpty() ? "Not found" : address);
        resultIntent.putExtra("date", date.isEmpty() ? "Not found" : date);
        resultIntent.putExtra("item", item.isEmpty() ? "Not found" : item);
        resultIntent.putExtra("order_id", orderId.isEmpty() ? "Not found" : orderId);
        resultIntent.putExtra("subtotal", subtotal.isEmpty() ? "Not found" : subtotal);
        resultIntent.putExtra("tax", tax.isEmpty() ? "Not found" : tax);
        resultIntent.putExtra("title", title.isEmpty() ? "Not found" : title);
        resultIntent.putExtra("total_price", totalPrice.isEmpty() ? "Not found" : totalPrice);
        resultIntent.putExtra("full_text", fullText); // Include full extracted text

        // For backward compatibility, map some fields to old names
        resultIntent.putExtra("name", title.isEmpty() ? "Not found" : title); // Map title to name
        resultIntent.putExtra("amount", totalPrice.isEmpty() ? "Not found" : totalPrice); // Map totalPrice to amount

        setResult(RESULT_OK, resultIntent);
        finish();
    }

    private void resetCaptureButton() {
        isCapturing = false;
        captureButton.setEnabled(true);
        captureButton.setText("Capture");
    }

    @Override
    public void onPreviewSizeChosen(final Size size, final int rotation) {
        final float textSizePx =
                TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, getResources().getDisplayMetrics());
        borderedText = new BorderedText(textSizePx);
        borderedText.setTypeface(Typeface.MONOSPACE);

        tracker = new MultiBoxTracker(this);

        final int modelIndex = modelView.getCheckedItemPosition();
        final String modelString = modelStrings.get(modelIndex);

        try {
            detector = DetectorFactory.getDetector(getAssets(), modelString);
        } catch (final IOException e) {
            e.printStackTrace();
            LOGGER.e(e, "Exception initializing classifier!");
            Toast toast =
                    Toast.makeText(
                            getApplicationContext(), "Classifier could not be initialized", Toast.LENGTH_SHORT);
            toast.show();
            finish();
        }

        int cropSize = detector.getInputSize();

        previewWidth = size.getWidth();
        previewHeight = size.getHeight();

        sensorOrientation = rotation - getScreenOrientation();
        LOGGER.i("Camera orientation relative to screen canvas: %d", sensorOrientation);

        LOGGER.i("Initializing at size %dx%d", previewWidth, previewHeight);
        rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Config.ARGB_8888);
        croppedBitmap = Bitmap.createBitmap(cropSize, cropSize, Config.ARGB_8888);

        frameToCropTransform =
                ImageUtils.getTransformationMatrix(
                        previewWidth, previewHeight,
                        cropSize, cropSize,
                        sensorOrientation, MAINTAIN_ASPECT);

        cropToFrameTransform = new Matrix();
        frameToCropTransform.invert(cropToFrameTransform);

        trackingOverlay = (OverlayView) findViewById(R.id.tracking_overlay);
        trackingOverlay.addCallback(
                new DrawCallback() {
                    @Override
                    public void drawCallback(final Canvas canvas) {
                        tracker.draw(canvas);
                        if (isDebug()) {
                            tracker.drawDebug(canvas);
                        }
                    }
                });

        tracker.setFrameConfiguration(previewWidth, previewHeight, sensorOrientation);
    }

    protected void updateActiveModel() {
        // Get UI information before delegating to background
        final int modelIndex = modelView.getCheckedItemPosition();
        final int deviceIndex = deviceView.getCheckedItemPosition();
        String threads = threadsTextView.getText().toString().trim();
        final int numThreads = Integer.parseInt(threads);

        handler.post(() -> {
            if (modelIndex == currentModel && deviceIndex == currentDevice
                    && numThreads == currentNumThreads) {
                return;
            }
            currentModel = modelIndex;
            currentDevice = deviceIndex;
            currentNumThreads = numThreads;

            // Disable classifier while updating
            if (detector != null) {
                detector.close();
                detector = null;
            }

            // Lookup names of parameters.
            String modelString = modelStrings.get(modelIndex);
            String device = deviceStrings.get(deviceIndex);

            LOGGER.i("Changing model to " + modelString + " device " + device);

            // Try to load model.

            try {
                detector = DetectorFactory.getDetector(getAssets(), modelString);
                // Customize the interpreter to the type of device we want to use.
                if (detector == null) {
                    return;
                }
            }
            catch(IOException e) {
                e.printStackTrace();
                LOGGER.e(e, "Exception in updateActiveModel()");
                Toast toast =
                        Toast.makeText(
                                getApplicationContext(), "Classifier could not be initialized", Toast.LENGTH_SHORT);
                toast.show();
                finish();
            }


            if (device.equals("CPU")) {
                detector.useCPU();
            } else if (device.equals("GPU")) {
                detector.useGpu();
            } else if (device.equals("NNAPI")) {
                detector.useNNAPI();
            }
            detector.setNumThreads(numThreads);

            int cropSize = detector.getInputSize();
            croppedBitmap = Bitmap.createBitmap(cropSize, cropSize, Config.ARGB_8888);

            frameToCropTransform =
                    ImageUtils.getTransformationMatrix(
                            previewWidth, previewHeight,
                            cropSize, cropSize,
                            sensorOrientation, MAINTAIN_ASPECT);

            cropToFrameTransform = new Matrix();
            frameToCropTransform.invert(cropToFrameTransform);
        });
    }

    @Override
    protected void processImage() {
        // Skip regular processing if we're capturing
        if (isCapturing) {
            readyForNextImage();
            return;
        }

        ++timestamp;
        final long currTimestamp = timestamp;
        trackingOverlay.postInvalidate();

        // No mutex needed as this method is not reentrant.
        if (computingDetection) {
            readyForNextImage();
            return;
        }
        computingDetection = true;
        LOGGER.i("Preparing image " + currTimestamp + " for detection in bg thread.");

        rgbFrameBitmap.setPixels(getRgbBytes(), 0, previewWidth, 0, 0, previewWidth, previewHeight);

        readyForNextImage();

        final Canvas canvas = new Canvas(croppedBitmap);
        canvas.drawBitmap(rgbFrameBitmap, frameToCropTransform, null);
        // For examining the actual TF input.
        if (SAVE_PREVIEW_BITMAP) {
            ImageUtils.saveBitmap(croppedBitmap);
        }

        runInBackground(
                new Runnable() {
                    @Override
                    public void run() {
                        LOGGER.i("Running detection on image " + currTimestamp);
                        final long startTime = SystemClock.uptimeMillis();
                        final List<Classifier.Recognition> results = detector.recognizeImage(croppedBitmap);
                        lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;

                        Log.e("CHECK", "run: " + results.size());

                        cropCopyBitmap = Bitmap.createBitmap(croppedBitmap);
                        final Canvas canvas = new Canvas(cropCopyBitmap);
                        final Paint paint = new Paint();
                        paint.setColor(Color.RED);
                        paint.setStyle(Style.STROKE);
                        paint.setStrokeWidth(2.0f);

                        float minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
                        switch (MODE) {
                            case TF_OD_API:
                                minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
                                break;
                        }

                        final List<Classifier.Recognition> mappedRecognitions =
                                new LinkedList<Classifier.Recognition>();

                        for (final Classifier.Recognition result : results) {
                            final RectF location = result.getLocation();
                            if (location != null && result.getConfidence() >= minimumConfidence) {
                                canvas.drawRect(location, paint);

                                cropToFrameTransform.mapRect(location);

                                result.setLocation(location);
                                mappedRecognitions.add(result);
                            }
                        }

                        tracker.trackResults(mappedRecognitions, currTimestamp);
                        trackingOverlay.postInvalidate();

                        computingDetection = false;

                        runOnUiThread(
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        showFrameInfo(previewWidth + "x" + previewHeight);
                                        showCropInfo(cropCopyBitmap.getWidth() + "x" + cropCopyBitmap.getHeight());
                                        showInference(lastProcessingTimeMs + "ms");
                                    }
                                });
                    }
                });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (textRecognizer != null) {
            textRecognizer.close();
        }
    }

    @Override
    protected int getLayoutId() {
        return R.layout.tfe_od_camera_connection_fragment_tracking;
    }

    @Override
    protected Size getDesiredPreviewFrameSize() {
        return DESIRED_PREVIEW_SIZE;
    }

    // Which detection model to use: by default uses Tensorflow Object Detection API frozen
    // checkpoints.
    private enum DetectorMode {
        TF_OD_API;
    }

    @Override
    protected void setUseNNAPI(final boolean isChecked) {
        runInBackground(() -> detector.setUseNNAPI(isChecked));
    }

    @Override
    protected void setNumThreads(final int numThreads) {
        runInBackground(() -> detector.setNumThreads(numThreads));
    }
}