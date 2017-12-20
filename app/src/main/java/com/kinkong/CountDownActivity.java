package com.kinkong;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Animatable;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.kinkong.database.FBDatabase;
import com.kinkong.database.data.Question;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import kin.sdk.core.Balance;
import kin.sdk.core.ResultCallback;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;


public class CountDownActivity extends BaseActivity {

    public static Intent getIntent(Context context) {
        return new Intent(context, CountDownActivity.class);
    }

    private final static String SERVER_TIME_URL = "https://us-central1-kinkong-977fc.cloudfunctions.net/date";
    private final static int MAX_HOURS = 100;
    private final static long MAX_HOURS_IN_MILLISECONDS = MAX_HOURS * 60 * 60 * 1000;
    private final static String TELEGRAM_LINK = "https://t.me/kinfoundation";
    private Question question;
    private Animatable animatable;
    private Thread animHourGlassThread;
    private TextView prize, balance, nextQuestionTitle;
    private ClockCountDownView clockCountDownView;
    private View prizeTelegram, joinTelegram;
    private boolean timerComplete;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.countdown_activity);
        question = FBDatabase.getInstance().nextQuestion;
        balance = findViewById(R.id.balance);
        ImageView progressHourGlass = findViewById(R.id.timer);
        animatable = ((Animatable) progressHourGlass.getDrawable());
        clockCountDownView = findViewById(R.id.clock_count_down);
        prizeTelegram = findViewById(R.id.prize_telegram);
        prize = findViewById(R.id.prize);
        joinTelegram = findViewById(R.id.join_telegram_title);
        nextQuestionTitle = findViewById(R.id.next_question_title);
        init();
    }

    private void init() {
        startThreadAnimation();
        initServerTime();
    }

    private void updatePendingBalance() {
        getApp().getKinClient().getAccount().getPendingBalance().run(new ResultCallback<Balance>() {
            @Override
            public void onResult(Balance balance) {
                updatePendingBalance(balance);
            }

            @Override
            public void onError(Exception e) {

            }
        });
    }

    private boolean isValidTime(long countDownTime) {
        return countDownTime > 0 && countDownTime < MAX_HOURS_IN_MILLISECONDS;
    }

    private void startCountDown(long countDownTime) {
        clockCountDownView.setListener(this::startQuestion);
        clockCountDownView.startCount(countDownTime);
    }

    private void startThreadAnimation() {
        animHourGlassThread = new Thread() {
            @Override
            public void run() {
                super.run();
                while (true) {
                    if (!animatable.isRunning()) {
                        startAnimation();
                    }
                }
            }
        };
        animHourGlassThread.start();
    }

    private void startAnimation() {
        runOnUiThread(() -> animatable.start());
    }

    @Override
    protected void onResume() {
        super.onResume();
        updatePendingBalance();
        startThreadAnimation();
        if (timerComplete) {
            int nextQuestionNum = FBDatabase.getInstance().nextQuestionNum;
            FBDatabase.getInstance().getQuestionAt(nextQuestionNum++, new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    question = dataSnapshot.getValue(Question.class);
                    if (question == null) {
                        Toast.makeText(CountDownActivity.this, "No More Questions for now... ", Toast.LENGTH_SHORT).show();
                        finish();
                    } else {
                        init();
                    }
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {
                }
            });
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        animHourGlassThread.interrupt();
    }

    private void updatePrize() {
        String prizeStr = getPrize() + " KIN";
        prize.setText(prizeStr);
    }

    private void updatePendingBalance(Balance accountBalance) {
        String balanceStr = accountBalance.value(1) + " KIN";
        balance.setText(balanceStr);
    }

    private int getPrize() {
        return question.getPrize();
    }

    private void initServerTime() {
        OkHttpClient okHttpClient = new OkHttpClient();
        Request request = new Request.Builder().url(SERVER_TIME_URL).build();
        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Toast.makeText(CountDownActivity.this, "Error loading data from server", Toast.LENGTH_LONG).show();
                finish();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    JSONObject jsonObject = new JSONObject(response.body().string());
                    long serverTime = Long.parseLong(jsonObject.get("time").toString());
                    initCountDown(serverTime);
                } catch (JSONException e) {
                    Toast.makeText(CountDownActivity.this, "Error loading data from server", Toast.LENGTH_LONG).show();
                    finish();
                }
            }
        });
    }

    private void initCountDown(long serverTime) {
        long time = question.getTimeStamp();
        long countDownTime = time - serverTime;
        updateUi(countDownTime);
    }

    private void updateUi(final long countDownTime) {
        runOnUiThread(() -> {
            if (isValidTime(countDownTime)) {
                updatePrize();
                startCountDown(countDownTime);
            } else {
                nextQuestionTitle.setText(getResources().getString(R.string.keep_me_posted));
                clockCountDownView.setVisibility(View.GONE);
                prizeTelegram.setVisibility(View.GONE);
                joinTelegram.setVisibility(View.VISIBLE);
            }
        });
    }

    private void startQuestion() {
        timerComplete = true;
        Intent questionIntent = QuestionVideoActivity.getIntent(this);
        if (startScreen(questionIntent)) {
            finish();
        }
    }

    public void startAccountInfo(View view) {
        Intent accountInfoIntent = AccountInfoActivity.getIntent(this);
        startScreen(accountInfoIntent);
    }

    public void startTutorial(View view) {
        Intent kinTutorialIntent = KinTutorial.getIntent(this, false);
        if (startScreen(kinTutorialIntent)) {
            finish();
        }
    }

    public void openTelegramGroup(View view) {
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(TELEGRAM_LINK));
        startActivity(browserIntent);
    }
}
