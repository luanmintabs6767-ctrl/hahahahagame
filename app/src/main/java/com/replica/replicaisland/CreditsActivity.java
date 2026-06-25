package com.replica.replicaisland;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.Window;

import java.util.ArrayList;
import java.util.Random;

public class CreditsActivity extends Activity {

    private CreditsView mCreditsView;
    private MediaPlayer mMediaPlayer;

    // 크레딧 개별 카드를 구성할 데이터 구조체
    private static class CreditItem {
        String role; String name; String deco;
        CreditItem(String role, String name, String deco) {
            this.role = role; this.name = name; this.deco = deco;
        }
    }

    // 💥 [한국어화 완수] 역할, 설명, 데코레이션까지 전부 한국어로 직관적 변경
    private static final CreditItem[] CREDITS = new CreditItem[] {
            new CreditItem("🎮 메인 프로그래머", "이솔", "✦ 시스템 설계 및 하드웨어 가속 코어 구축 ✦"),
            new CreditItem("🎨 리드 그래픽 디자인", "박은찬", "◈ 도트 픽셀 아트 및 게임 인터페이스 총괄 ◈"),
            new CreditItem("🧪 QA 총괄 팀장", "황정훈", "⬥ 게임 밸런스 및 하이엔드 레벨 검증 ⬥"),
            new CreditItem("🧪 QA 베타 테스터", "황도연", "⬥ 구형 기기 최적화 및 스테이지 버그 추적 ⬥"),
            new CreditItem("🧪 QA 베타 테스터", "임지호", "⬥ 캐릭터 물리 매트릭스 타격감 검증 ⬥"),
            new CreditItem("🏛️ 원작 크리에이터", "크리스 프루ett", "★ 레플리카 아일랜드 오리지널 개발자 ★"),
            new CreditItem("🤖 안드로이드 팀", "구글 오픈소스", "📱 프레임워크 베이스 아키텍처 기여 📱"),
            new CreditItem("🔥 엔진 컨버전", "순수 자바 시스템", "⚡ 고성능 서피스 뷰 데이터 렌더러 탑재 ⚡"),
            new CreditItem("🎵 오디오 트랙", "크레딧 테마곡", "♫ 전용 스테레오 배경 사운드트랙 연동 ♫"),
            new CreditItem("🔊 사운드 이펙트", "레플리카 음향 팀", "🔊 다이내믹 게임오디오 효과음 배치 🔊"),
            new CreditItem("🪵 레벨 디자인", "스테이지 매트릭스", "⚙️ 절차적 맵 에디터 빌드 공정 완성 ⚙️"),
            new CreditItem("✒️ 적용 타이포그래피", "모노스페이스 볼드", "🔤 고전 감성 비트맵 폰트 제어 시스템 🔤"),
            new CreditItem("🎛️ 메모리 가비지 통제", "비트맵 리사이클러", "🗑️ 런타임 메모리 OOM 크래시 방어 코어 🗑️"),
            new CreditItem("🛡️ 오픈소스 라이선스", "아파치 라이선스 2.0", "📝 자유 소프트웨어 배포 규약 적용 📝"),
            new CreditItem("🗺️ 월드 빌더 맵", "아일랜드 스테이지", "🏔️ 모험 에리얼 월드 01-12 디자인 🏔️"),
            new CreditItem("🦖 등장 몬스터 개체", "올스타 에너미즈", "👾 적 유닛 인공지능 매트릭스 구현 👾"),
            new CreditItem("✨ 특별 감사", "플레이어 여러분", "♥ 마지막 순간까지 함께해주셔서 진심으로 감사합니다 ♥"),
            new CreditItem("👑 레플리카 아일랜드", "하하하하게임 프로젝트", "🏁 모든 시스템 매트릭스 분석 완벽 통과 🏁")
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
                android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN,
                android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN
        );

        mCreditsView = new CreditsView(this);
        setContentView(mCreditsView);

        mMediaPlayer = MediaPlayer.create(this, R.raw.credits);
        if (mMediaPlayer != null) mMediaPlayer.setLooping(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mCreditsView.resume();
        if (mMediaPlayer != null && !mMediaPlayer.isPlaying()) mMediaPlayer.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mCreditsView.pause();
        if (mMediaPlayer != null && mMediaPlayer.isPlaying()) {
            mMediaPlayer.pause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mCreditsView != null) mCreditsView.recycleBitmaps();
        if (mMediaPlayer != null) { mMediaPlayer.release(); mMediaPlayer = null; }
    }

    private class CreditsView extends SurfaceView implements Runnable {
        private Thread mRenderThread = null;
        private SurfaceHolder mHolder;
        private volatile boolean mRunning = false;

        private String mPhase = "running";
        private long mTotalDuration = 0;
        private long mLastTime = 0;

        private Bitmap[] mAndouRunBmp = new Bitmap[3];
        private Bitmap[] mWandaRunBmp = new Bitmap[2];
        private Bitmap[] mKabochaRunBmp = new Bitmap[2];
        private Bitmap[] mKyleRunBmp = new Bitmap[2];
        private Bitmap[] mSkeletonRunBmp = new Bitmap[2];
        private Bitmap mBmpSlime, mBmpBat, mAndouHit;

        private ArrayList<Star> mStars = new ArrayList<Star>();
        private ArrayList<Cloud> mClouds = new ArrayList<Cloud>();
        private ArrayList<Particle> mParticles = new ArrayList<Particle>();
        private ArrayList<Runner> mAllRunners = new ArrayList<Runner>();

        private int mCurrentCreditIdx = -1;
        private float mCreditAlpha = 0f;
        private long mRushTimer = 0;
        private long mRushInterval = 450;

        private Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Random mRand = new Random();
        private int mScreenWidth, mScreenHeight;
        private boolean mSizeInitialized = false;

        private float mGroundOffsetX = 0f;

        // 💥 [연출 극대화] 시네마틱 화면 지진(Shake) 효과용 물리 오프셋 변수
        private float mShakeX = 0f;
        private float mShakeY = 0f;

        public CreditsView(Context context) {
            super(context);
            mHolder = getHolder();
            loadSprites(context);
        }

        private void loadSprites(Context context) {
            BitmapFactory.Options opt = new BitmapFactory.Options();
            opt.inScaled = false;
            opt.inPreferredConfig = Bitmap.Config.ARGB_4444;

            mAndouRunBmp[0] = BitmapFactory.decodeResource(context.getResources(), R.drawable.andou_flyup01, opt);
            mAndouRunBmp[1] = BitmapFactory.decodeResource(context.getResources(), R.drawable.andou_flyup02, opt);
            mAndouRunBmp[2] = BitmapFactory.decodeResource(context.getResources(), R.drawable.andou_flyup03, opt);
            mAndouHit = BitmapFactory.decodeResource(context.getResources(), R.drawable.andou_hit, opt);

            mWandaRunBmp[0] = BitmapFactory.decodeResource(context.getResources(), R.drawable.enemy_wanda_run01, opt);
            mWandaRunBmp[1] = BitmapFactory.decodeResource(context.getResources(), R.drawable.enemy_wanda_run03, opt);

            mKabochaRunBmp[0] = BitmapFactory.decodeResource(context.getResources(), R.drawable.enemy_kabocha_walk01, opt);
            mKabochaRunBmp[1] = BitmapFactory.decodeResource(context.getResources(), R.drawable.enemy_kabocha_walk03, opt);

            mKyleRunBmp[0] = BitmapFactory.decodeResource(context.getResources(), R.drawable.enemy_kyle_walk01, opt);
            mKyleRunBmp[1] = BitmapFactory.decodeResource(context.getResources(), R.drawable.enemy_kyle_walk04, opt);

            mSkeletonRunBmp[0] = BitmapFactory.decodeResource(context.getResources(), R.drawable.enemy_skeleton_walk01, opt);
            mSkeletonRunBmp[1] = BitmapFactory.decodeResource(context.getResources(), R.drawable.enemy_skeleton_walk03, opt);

            mBmpSlime = BitmapFactory.decodeResource(context.getResources(), R.drawable.enemy_shadowslime_stand, opt);
            mBmpBat = BitmapFactory.decodeResource(context.getResources(), R.drawable.enemy_bat01, opt);
        }

        public void recycleBitmaps() {
            for (Bitmap b : mAndouRunBmp) { if (b != null) b.recycle(); }
            for (Bitmap b : mWandaRunBmp) { if (b != null) b.recycle(); }
            for (Bitmap b : mKabochaRunBmp) { if (b != null) b.recycle(); }
            for (Bitmap b : mKyleRunBmp) { if (b != null) b.recycle(); }
            for (Bitmap b : mSkeletonRunBmp) { if (b != null) b.recycle(); }
            if (mAndouHit != null) mAndouHit.recycle();
            if (mBmpSlime != null) mBmpSlime.recycle();
            if (mBmpBat != null) mBmpBat.recycle();
        }

        private void initSceneSize(int w, int h) {
            mScreenWidth = w; mScreenHeight = h;
            for (int i = 0; i < 110; i++) mStars.add(new Star(mRand.nextFloat() * w, mRand.nextFloat() * (h * 0.6f)));
            for (int i = 0; i < 6; i++) mClouds.add(new Cloud(mRand.nextFloat() * w, 20f + mRand.nextFloat() * 100f));
            mSizeInitialized = true;
        }

        private void setupGrandChase() {
            mAllRunners.clear();
            float baseFloor = mScreenHeight - 120f;
            mAllRunners.add(new Runner(mAndouRunBmp, mScreenWidth * 0.76f, baseFloor - mAndouRunBmp[0].getHeight() * 2.3f + 10f, 2.3f, true));
            mAllRunners.add(new Runner(mWandaRunBmp, mScreenWidth * 0.56f, baseFloor - mWandaRunBmp[0].getHeight() * 2.1f + 10f, 2.1f, false));
            mAllRunners.add(new Runner(mKyleRunBmp, mScreenWidth * 0.42f, baseFloor - mKyleRunBmp[0].getHeight() * 2.2f + 10f, 2.2f, false));
            mAllRunners.add(new Runner(mKabochaRunBmp, mScreenWidth * 0.29f, baseFloor - mKabochaRunBmp[0].getHeight() * 2.2f + 10f, 2.2f, false));
            mAllRunners.add(new Runner(mSkeletonRunBmp, mScreenWidth * 0.17f, baseFloor - mSkeletonRunBmp[0].getHeight() * 2.1f + 10f, 2.1f, false));

            Bitmap[] slimeSheet = { mBmpSlime, mBmpSlime };
            mAllRunners.add(new Runner(slimeSheet, mScreenWidth * 0.04f, baseFloor - mBmpSlime.getHeight() * 2.0f + 10f, 2.0f, false));
            Bitmap[] batSheet = { mBmpBat, mBmpBat };
            mAllRunners.add(new Runner(batSheet, mScreenWidth * 0.51f, mScreenHeight * 0.28f, 2.0f, true));
        }

        public void resume() {
            mRunning = true; mLastTime = System.currentTimeMillis();
            mRenderThread = new Thread(this); mRenderThread.start();
        }

        public void pause() {
            mRunning = false;
            try { mRenderThread.join(); } catch (InterruptedException e) { e.printStackTrace(); }
        }

        @Override
        public void run() {
            while (mRunning) {
                if (!mHolder.getSurface().isValid()) continue;
                Canvas canvas = mHolder.lockCanvas();
                if (canvas == null) continue;

                if (!mSizeInitialized) initSceneSize(canvas.getWidth(), canvas.getHeight());

                long now = System.currentTimeMillis();
                long dt = now - mLastTime;
                if (dt > 100) dt = 16;
                mLastTime = now;

                if (!"final".equals(mPhase)) mTotalDuration += dt;

                updateEngine(dt);
                drawEngine(canvas);
                mHolder.unlockCanvasAndPost(canvas);
            }
        }

        private void updateEngine(long dt) {
            long duration = mTotalDuration;

            if ("running".equals(mPhase)) {
                if (duration < 5000) { mCurrentCreditIdx = 0; mCreditAlpha = 1f; }
                else if (duration < 10000) { mCurrentCreditIdx = 1; }
                else if (duration < 14000) { mCurrentCreditIdx = 2; }
                else if (duration < 18000) { mCurrentCreditIdx = 3; }
                else if (duration < 22000) { mCurrentCreditIdx = 4; }
                else if (duration < 33000) { mCreditAlpha = 0f; }

                if (duration >= 33000) {
                    mPhase = "rush";
                    mRushTimer = 0; mRushInterval = 450;
                    mCurrentCreditIdx = 0; mCreditAlpha = 1f;
                }
            }
            else if ("rush".equals(mPhase)) {
                mRushTimer += dt;
                if (mRushTimer >= mRushInterval) {
                    mRushTimer = 0;
                    mCurrentCreditIdx++;
                    mRushInterval = Math.max(mRushInterval - 50, 100);
                    if (mCurrentCreditIdx >= CREDITS.length) mCurrentCreditIdx = CREDITS.length - 1;
                }
                // 38초 ~ 38.5초: 자막을 멈추고 고요한 여백 연출
                if (duration >= 38000) {
                    mPhase = "wait_half";
                    mCreditAlpha = 0f;
                }
            }
            else if ("wait_half".equals(mPhase)) {
                // 38.5초 ~ 39.0초: 극도의 긴장감을 유도하는 0.5초 초고속 암전 단계
                if (duration >= 38500) {
                    mPhase = "black";
                }
            }
            else if ("black".equals(mPhase)) {
                // 39.0초 정각: 마침내 전원 폭발적 초고속 런 시퀀스 발동
                if (duration >= 39000) {
                    mPhase = "grand_chase";
                    setupGrandChase();
                }
            }
            else if ("grand_chase".equals(mPhase)) {
                mGroundOffsetX -= dt * 0.95f; // 전진 속도를 극대화하여 공간감 확장
                if (mGroundOffsetX <= -80f) mGroundOffsetX = 0f;

                // 💥 [연출 극대화] 올스타 레이싱 질주 시 화면을 무작위로 요동치게 만들어 타격감 연출
                mShakeX = (mRand.nextFloat() - 0.5f) * 14f;
                mShakeY = (mRand.nextFloat() - 0.5f) * 14f;

                for (Runner r : mAllRunners) r.update(dt, mScreenWidth);

                if (mRand.nextFloat() < 0.55f) {
                    mParticles.add(new Particle(mRand.nextFloat() * mScreenWidth, mScreenHeight - 120f, Particle.TYPE_DUST));
                    mParticles.add(new Particle(mRand.nextFloat() * mScreenWidth, mScreenHeight - 120f, Particle.TYPE_GRASS));
                }
                if (mRand.nextFloat() < 0.20f) {
                    mParticles.add(new Particle(mScreenWidth * 0.60f + (mRand.nextFloat() * 120f), mScreenHeight - 220f, Particle.TYPE_HEART));
                }

                if (duration >= 56000) {
                    mPhase = "final";
                    mShakeX = 0f; mShakeY = 0f; // 진동 정지
                }
            }

            boolean speedStars = "grand_chase".equals(mPhase);
            for (Star s : mStars) s.update(dt, speedStars);
            for (Cloud c : mClouds) c.update(dt, mScreenWidth);
            for (int i = mParticles.size() - 1; i >= 0; i--) {
                if (!mParticles.get(i).update(dt)) mParticles.remove(i);
            }
        }

        private void drawEngine(Canvas canvas) {
            if ("black".equals(mPhase)) {
                canvas.drawColor(Color.BLACK);
                return;
            }

            // 💥 [연출 극대화] 지진 효과 오프셋 매트릭스를 전역 캔버스 트랜스레이트에 강제 결합
            canvas.save();
            canvas.translate(mShakeX, mShakeY);

            int topColor = 0xFF120321; int bottomColor = 0xFFFFA07A;
            if ("grand_chase".equals(mPhase)) {
                // 피날레 구간: 네온 오로라 스카이 버퍼 셰이더 적용
                topColor = 0xFF02002A; bottomColor = 0xFF00FFCC;
            } else if ("final".equals(mPhase)) {
                topColor = 0xFF05030A; bottomColor = 0xFF111126;
            }

            LinearGradient shader = new LinearGradient(0, 0, 0, mScreenHeight, topColor, bottomColor, Shader.TileMode.CLAMP);
            mPaint.setShader(shader); canvas.drawRect(0, 0, mScreenWidth, mScreenHeight, mPaint); mPaint.setShader(null);

            mPaint.setColor(Color.WHITE);
            for (Star s : mStars) {
                mPaint.setAlpha((int) (s.alpha * 255)); canvas.drawCircle(s.x, s.y, s.size, mPaint);
            }
            mPaint.setAlpha(255);

            for (Cloud c : mClouds) {
                mPaint.setColor(Color.argb(40, 255, 255, 255));
                canvas.drawRoundRect(new RectF(c.x, c.y, c.x + c.w, c.y + c.h), 20f, 20f, mPaint);
            }

            mPaint.setColor("grand_chase".equals(mPhase) ? 0xFF0D2B14 : 0xFF226E39);
            canvas.drawRect(0, mScreenHeight - 120f, mScreenWidth, mScreenHeight, mPaint);

            mPaint.setColor(0xFF6EE77A);
            canvas.drawRect(0, mScreenHeight - 120f, mScreenWidth, mScreenHeight - 114f, mPaint);
            if ("grand_chase".equals(mPhase)) {
                mPaint.setColor(0xFF00FF66); // 바닥 격자 텍스처 패턴도 네온 그린으로 발광 효과 부여
                mPaint.setStrokeWidth(5f);
                for (float sx = mGroundOffsetX; sx < mScreenWidth; sx += 40f) {
                    canvas.drawLine(sx, mScreenHeight - 114f, sx + 22f, mScreenHeight, mPaint);
                }
            }

            for (Particle p : mParticles) {
                if (p.type == Particle.TYPE_DUST) {
                    mPaint.setColor(Color.argb((int)(p.alpha * 180), 240, 240, 240)); canvas.drawCircle(p.x, p.y, p.size, mPaint);
                } else if (p.type == Particle.TYPE_GRASS) {
                    mPaint.setColor(Color.argb((int)(p.alpha * 240), 0, 255, 102));
                    canvas.drawRect(p.x, p.y, p.x + p.size, p.y + p.size, mPaint);
                } else {
                    mPaint.setColor(Color.RED); mPaint.setTextSize(35f); mPaint.setAlpha((int)(p.alpha * 255));
                    canvas.drawText("❤", p.x, p.y, mPaint);
                }
            }
            mPaint.setAlpha(255);

            if ("grand_chase".equals(mPhase)) {
                for (Runner r : mAllRunners) {
                    canvas.save();
                    // 수직 점프값 완벽 배제, 오직 수평 X축 관성 벡터 엔진 스위칭
                    canvas.translate(r.x + r.dashXOffset, r.y + r.floatOffset);
                    canvas.scale(r.scale, r.scale);
                    canvas.drawBitmap(r.spriteSheet[r.currentFrame], 0, 0, null);
                    canvas.restore();
                }
            } else if ("running".equals(mPhase) || "rush".equals(mPhase)) {
                canvas.save();
                int frame = (int)((mTotalDuration / 130) % 3);
                canvas.translate(mScreenWidth * 0.45f, mScreenHeight - 200f + (float)Math.sin(mTotalDuration * 0.006d)*5f);
                canvas.scale(2.3f, 2.3f);
                canvas.drawBitmap(mAndouRunBmp[frame], 0, 0, null);
                canvas.restore();
            }

            if (mCreditAlpha > 0 && mCurrentCreditIdx >= 0 && mCurrentCreditIdx < CREDITS.length) {
                drawCreditText(canvas);
            }

            if ("final".equals(mPhase)) {
                drawFinalTeamScreen(canvas);
            }

            canvas.restore(); // 지진 효과 오프셋 롤백 복구
        }

        private void drawCreditText(Canvas canvas) {
            CreditItem item = CREDITS[mCurrentCreditIdx];
            mPaint.setTextAlign(Paint.Align.CENTER); mPaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));

            mPaint.setColor(0xFFFFD700); mPaint.setTextSize("rush".equals(mPhase) ? 44f : 32f);
            canvas.drawText(item.role, mScreenWidth * 0.5f, mScreenHeight * 0.35f, mPaint);

            mPaint.setColor(Color.WHITE); mPaint.setTextSize("rush".equals(mPhase) ? 88f : 70f);
            canvas.drawText(item.name, mScreenWidth * 0.5f, mScreenHeight * 0.52f, mPaint);

            mPaint.setColor(0xFFFF6B6B); mPaint.setTextSize(32f);
            canvas.drawText(item.deco, mScreenWidth * 0.5f, mScreenHeight * 0.66f, mPaint);
            mPaint.setTextAlign(Paint.Align.LEFT);
        }

        private void drawFinalTeamScreen(Canvas canvas) {
            canvas.drawColor(0xFF04020A);
            mPaint.setTextAlign(Paint.Align.CENTER); mPaint.setTypeface(Typeface.DEFAULT_BOLD);
            mPaint.setColor(0xFF7CF77C); mPaint.setTextSize(65f); canvas.drawText("크레딧 시스템 가동 종료", mScreenWidth * 0.5f, mScreenHeight * 0.18f, mPaint);

            mPaint.setTextSize(24f); float startY = mScreenHeight * 0.30f;
            for (int i = 0; i < Math.min(CREDITS.length, 10); i++) {
                CreditItem c = CREDITS[i]; mPaint.setColor(0xFFFFD700);
                canvas.drawText(c.role + " : ", mScreenWidth * 0.38f, startY + (i * 38f), mPaint);
                mPaint.setColor(Color.WHITE); canvas.drawText(c.name, mScreenWidth * 0.64f, startY + (i * 38f), mPaint);
            }
            mPaint.setColor(0xFFA8A8FF); mPaint.setTextSize(30f);
            canvas.drawText("❤ 플레이해주셔서 머리 숙여 감사드립니다 ❤", mScreenWidth * 0.5f, mScreenHeight * 0.90f, mPaint);
            mPaint.setTextAlign(Paint.Align.LEFT);
        }

        private class Star {
            float x, y, size, alpha, speed;
            Star(float x, float y) {
                this.x = x; this.y = y; this.size = (float) Math.random() * 2.5f + 1f;
                this.alpha = (float) Math.random(); this.speed = 0.0015f + (float) Math.random() * 0.002f;
            }
            void update(long dt, boolean speedChase) {
                if (speedChase) {
                    x -= dt * 0.55f; // 별빛 스크롤 속도 속도감 조절
                    if (x < 0) x = mScreenWidth;
                } else {
                    alpha += speed * dt; if (alpha > 1f || alpha < 0f) speed = -speed;
                }
            }
        }

        private class Cloud {
            float x, y, w, h, speed;
            Cloud(float x, float y) {
                this.x = x; this.y = y;
                this.w = 150f + (float) Math.random() * 100f; this.h = 35f + (float) Math.random() * 20f;
                this.speed = 0.02f + (float) Math.random() * 0.03f;
            }
            void update(long dt, int sw) {
                x -= speed * dt; if (x < -w) x = sw + 50f;
            }
        }
    }

    private static class Runner {
        Bitmap[] spriteSheet;
        int currentFrame = 0;
        long frameTimer = 0;
        float x, y, scale;
        float dashXOffset, floatOffset;
        boolean isFloating; long waveTimer; float speedFactor;

        Runner(Bitmap[] sheet, float x, float y, float scale, boolean floating) {
            this.spriteSheet = sheet; this.x = x; this.y = y; this.scale = scale;
            this.isFloating = floating; this.waveTimer = new Random().nextInt(1000);
            this.speedFactor = 0.12f + new Random().nextFloat() * 0.18f;
        }

        void update(long dt, int screenWidth) {
            waveTimer += dt;
            frameTimer += dt;

            if (frameTimer >= 110) {
                frameTimer = 0;
                currentFrame = (currentFrame + 1) % spriteSheet.length;
            }

            if (isFloating) {
                floatOffset = (float) Math.sin(waveTimer * 0.007d) * 20f;
            } else {
                floatOffset = 0f;
            }

            dashXOffset += (float) Math.sin(waveTimer * 0.0034d * speedFactor) * (dt * 0.75f);
            if (x + dashXOffset < 10f) dashXOffset = 10f - x;
            if (x + dashXOffset > screenWidth - 120f) dashXOffset = (screenWidth - 120f) - x;
        }
    }

    private static class Particle {
        static final int TYPE_DUST = 0; static final int TYPE_HEART = 1; static final int TYPE_GRASS = 2;
        float x, y, size, alpha; int type;
        Particle(float x, float y, int type) {
            this.x = x; this.y = y; this.type = type;
            this.size = type == TYPE_DUST ? 9f : (type == TYPE_GRASS ? 6f : 1f); this.alpha = 1f;
        }
        boolean update(long dt) {
            x -= dt * (type == TYPE_GRASS ? 0.65f : 0.48f);
            y -= dt * (type == TYPE_DUST ? 0.02f : (type == TYPE_GRASS ? -0.05f : 0.04f));
            alpha -= dt * (type == TYPE_DUST ? 0.005f : (type == TYPE_GRASS ? 0.007f : 0.0018f));
            if (type == TYPE_DUST || type == TYPE_GRASS) size -= dt * 0.006f;
            return alpha > 0;
        }
    }
}