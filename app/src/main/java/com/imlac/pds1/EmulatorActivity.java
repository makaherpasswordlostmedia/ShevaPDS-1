package com.imlac.pds1;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.*;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import java.util.List;

public class EmulatorActivity extends Activity {

    private Machine    machine;
    private Demos      demos;
    private CrtView    crtView;
    private GameLoader gameLoader;

    private Thread   mpThread;
    private volatile boolean mpRunning = false;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private TextView tvPC, tvAC, tvIR, tvLink, tvDPX, tvDPY, tvStatus, tvFps;
    private SeekBar  sbFps;
    private int      targetFps = 30;

    // Virtual controller
    private final boolean[] ctrl = new boolean[8];
    private static final int K_UP=0,K_DN=1,K_LT=2,K_RT=3,K_A=4,K_B=5,K_C=6,K_D=7;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                             WindowManager.LayoutParams.FLAG_FULLSCREEN);
        hideSystemUI();
        setContentView(R.layout.activity_emulator);

        machine    = new Machine();
        demos      = new Demos(machine);
        gameLoader = new GameLoader(this);

        crtView = findViewById(R.id.crt_view);
        crtView.setMachine(machine, demos);
        crtView.setMaxFps(30);

        findViews();
        wireControlPanel();
        wireVirtualController();
        wireGameMenu();
        startRegUpdater();

        crtView.setOnTouchListener((v, ev) -> {
            int[] p = crtView.screenToPDS(ev.getX(), ev.getY());
            machine.lpen_x = p[0]; machine.lpen_y = p[1];
            machine.lpen_hit = (ev.getAction() != MotionEvent.ACTION_UP);
            return true;
        });

        demos.setDemo(Demos.Type.STAR);
    }

    @Override protected void onResume()  { super.onResume(); hideSystemUI(); }
    @Override protected void onDestroy() { super.onDestroy(); stopMP(); uiHandler.removeCallbacksAndMessages(null); }

    private void hideSystemUI() {
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    private void findViews() {
        tvPC   = findViewById(R.id.reg_pc);
        tvAC   = findViewById(R.id.reg_ac);
        tvIR   = findViewById(R.id.reg_ir);
        tvLink = findViewById(R.id.reg_link);
        tvDPX  = findViewById(R.id.reg_dpx);
        tvDPY  = findViewById(R.id.reg_dpy);
        tvStatus = findViewById(R.id.status_text);
        tvFps  = findViewById(R.id.tv_fps);
        sbFps  = findViewById(R.id.sb_fps);
    }

    private void startRegUpdater() {
        uiHandler.postDelayed(new Runnable() {
            @Override public void run() {
                if (tvPC == null) return;
                tvPC  .setText(String.format("PC:%04X", machine.mp_pc));
                tvAC  .setText(String.format("AC:%04X", machine.mp_ac));
                tvIR  .setText(String.format("IR:%04X", machine.mp_ir));
                tvLink.setText(String.format("L:%d",    machine.mp_link));
                tvDPX .setText(String.format("DX:%03X", machine.dp_x));
                tvDPY .setText(String.format("DY:%03X", machine.dp_y));
                tvStatus.setText(machine.mp_halt ? "HALT" : "RUN ");
                tvStatus.setTextColor(machine.mp_halt ? 0xFFFF3300 : 0xFF00FF41);
                if (tvFps != null) tvFps.setText(String.format("%.0f/"+targetFps+"fps", crtView.getActualFps()));
                uiHandler.postDelayed(this, 100);
            }
        }, 100);
    }

    private void startMP() {
        stopMP();
        mpRunning = true;
        mpThread = new Thread(() -> {
            while (mpRunning) {
                // ALWAYS update keyboard from controller (even when MP halted)
                int key = 0;
                if (ctrl[K_UP]) key='W'; else if (ctrl[K_DN]) key='S';
                else if (ctrl[K_LT]) key='A'; else if (ctrl[K_RT]) key='D';
                else if (ctrl[K_A])  key=' '; else if (ctrl[K_B])  key='F';
                else if (ctrl[K_C])  key='E'; else if (ctrl[K_D])  key='Q';
                machine.keyboard = (key!=0) ? (key|0x8000) : 0;

                // Run MP only when not halted
                if (!machine.mp_halt && machine.mp_run) {
                    for (int i = 0; i < 10000 && !machine.mp_halt; i++)
                        machine.mpStep();
                }
                try { Thread.sleep(16); } catch (InterruptedException e) { break; }
            }
        }, "imlac-mp");
        mpThread.setDaemon(true);
        mpThread.start();
    }

    private void stopMP() {
        mpRunning = false;
        if (mpThread != null) try { mpThread.join(300); } catch (InterruptedException ignored) {}
    }

    private void wireControlPanel() {
        Button btnPwr  = findViewById(R.id.btn_power);
        Button btnRst  = findViewById(R.id.btn_reset);
        Button btnRun  = findViewById(R.id.btn_run);
        Button btnHalt = findViewById(R.id.btn_halt);
        Button btnStep = findViewById(R.id.btn_step);

        if (btnPwr  != null) btnPwr .setOnClickListener(v -> { machine.powerOn(); startMP(); });
        if (btnRst  != null) btnRst .setOnClickListener(v -> { machine.reset(); machine.mp_halt=false; machine.mp_run=true; });
        if (btnRun  != null) btnRun .setOnClickListener(v -> { machine.mp_halt=false; machine.mp_run=true; startMP(); });
        if (btnHalt != null) btnHalt.setOnClickListener(v -> { machine.mp_halt=true; machine.mp_run=false; });
        if (btnStep != null) btnStep.setOnClickListener(v -> { machine.mp_halt=false; machine.mpStep(); machine.mp_halt=true; });

        if (sbFps != null) {
            sbFps.setMax(59);
            sbFps.setProgress(29);
            sbFps.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                public void onProgressChanged(SeekBar s, int p, boolean u) {
                    targetFps = p+1;
                    crtView.setMaxFps(targetFps);
                }
                public void onStartTrackingTouch(SeekBar s) {}
                public void onStopTrackingTouch(SeekBar s)  {}
            });
        }

        wireDemo(R.id.btn_demo_star,      Demos.Type.STAR);
        wireDemo(R.id.btn_demo_lines,     Demos.Type.LINES);
        wireDemo(R.id.btn_demo_lissajous, Demos.Type.LISSAJOUS);
        wireDemo(R.id.btn_demo_bounce,    Demos.Type.BOUNCE);
        wireDemo(R.id.btn_demo_maze,      Demos.Type.MAZE);
        wireDemo(R.id.btn_demo_spacewar,  Demos.Type.SPACEWAR);
        Button btnMW = findViewById(R.id.btn_mazewar);
        if (btnMW != null) btnMW.setOnClickListener(v -> {
            demos.setDemo(Demos.Type.MAZEWAR);
            machine.mp_halt = true;  // Maze War runs its own loop
            demos.setMazeWarInput(ctrl);  // share ctrl array with game
        });
    }

    private void wireDemo(int id, Demos.Type type) {
        Button b = findViewById(id);
        if (b!=null) b.setOnClickListener(v -> { demos.setDemo(type); machine.mp_halt=true; });
    }

    @SuppressWarnings("ClickableViewAccessibility")
    private void wireVirtualController() {
        wireDpad(R.id.dp_up,    K_UP);
        wireDpad(R.id.dp_down,  K_DN);
        wireDpad(R.id.dp_left,  K_LT);
        wireDpad(R.id.dp_right, K_RT);
        wireDpad(R.id.btn_a,    K_A);
        wireDpad(R.id.btn_b,    K_B);
        wireDpad(R.id.btn_c,    K_C);
        wireDpad(R.id.btn_d,    K_D);
    }

    @SuppressWarnings("ClickableViewAccessibility")
    private void wireDpad(int id, int ki) {
        View v = findViewById(id);
        if (v==null) return;
        v.setOnTouchListener((vv, ev) -> {
            boolean down = ev.getAction()==MotionEvent.ACTION_DOWN||ev.getAction()==MotionEvent.ACTION_MOVE;
            ctrl[ki] = down;
            vv.setAlpha(down ? 0.45f : 1.0f);
            return true;
        });
    }

    private void wireGameMenu() {
        Button b = findViewById(R.id.btn_games);
        if (b!=null) b.setOnClickListener(v -> showGameMenu());
    }

    private void showGameMenu() {
        List<GameLoader.Game> list = gameLoader.getGames();
        String[] items = new String[list.size()+3];
        items[0] = "+ New game";
        items[1] = "Example: Counter";
        items[2] = "Example: Draw Box";
        for (int i=0;i<list.size();i++) items[i+3] = list.get(i).name;

        new AlertDialog.Builder(this)
            .setTitle("Games / Programs")
            .setItems(items, (d,w) -> {
                if (w==0) showEditorDialog(null);
                else if (w==1) showEditorDialog(new GameLoader.Game("counter",GameLoader.EXAMPLE_COUNTER,"Binary counter"));
                else if (w==2) showEditorDialog(new GameLoader.Game("drawbox",GameLoader.EXAMPLE_DRAW_BOX,"Draw a box"));
                else showGameOptions(list.get(w-3));
            })
            .setNegativeButton("Close",null).show();
    }

    private void showGameOptions(GameLoader.Game g) {
        new AlertDialog.Builder(this)
            .setTitle(g.name)
            .setMessage(g.desc.isEmpty() ? "(no description)" : g.desc)
            .setPositiveButton("Run",    (d,w) -> runGame(g))
            .setNeutralButton ("Edit",   (d,w) -> showEditorDialog(g))
            .setNegativeButton("Delete", (d,w) -> { gameLoader.deleteGame(g.name); })
            .show();
    }

    private void showEditorDialog(GameLoader.Game existing) {
        View dlgView = getLayoutInflater().inflate(R.layout.dialog_editor, null);
        EditText etName   = dlgView.findViewById(R.id.et_name);
        EditText etDesc   = dlgView.findViewById(R.id.et_desc);
        EditText etSource = dlgView.findViewById(R.id.et_source);

        if (existing!=null) {
            etName  .setText(existing.name);
            etDesc  .setText(existing.desc);
            etSource.setText(existing.source);
        } else {
            etSource.setText("; Imlac PDS-1 Assembly\n; WASD=move  SPACE=fire\n\n        ORG 0050\nSTART:  NOP\n        JMP START\n");
        }

        new AlertDialog.Builder(this)
            .setTitle(existing==null ? "New Game" : "Edit: "+existing.name)
            .setView(dlgView)
            .setPositiveButton("Save & Run", (d,w) -> {
                String name = etName.getText().toString().trim();
                if (name.isEmpty()) name="untitled";
                gameLoader.saveGame(name, etSource.getText().toString(), etDesc.getText().toString());
                GameLoader.Game saved = gameLoader.findGame(name);
                if (saved!=null) runGame(saved);
            })
            .setNeutralButton("Save only", (d,w) -> {
                String name = etName.getText().toString().trim();
                if (name.isEmpty()) name="untitled";
                gameLoader.saveGame(name, etSource.getText().toString(), etDesc.getText().toString());
                Toast.makeText(this,"Saved",Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancel",null).show();
    }

    private void runGame(GameLoader.Game g) {
        machine.reset();
        int words = machine.assemble(g.source);
        machine.mp_pc = 0x050;
        machine.mp_halt = false;
        machine.mp_run  = true;
        demos.setDemo(Demos.Type.USER_ASM);
        startMP();
        Toast.makeText(this, g.name+" ("+words+" words)", Toast.LENGTH_SHORT).show();
    }

    @Override public boolean onKeyDown(int kc, KeyEvent ev) {
        int a = keyToAscii(kc);
        if (a!=0) { machine.keyboard=a|0x8000; return true; }
        return super.onKeyDown(kc,ev);
    }
    @Override public boolean onKeyUp(int kc, KeyEvent ev) {
        machine.keyboard=0; return super.onKeyUp(kc,ev);
    }
    private int keyToAscii(int kc) {
        if (kc>=KeyEvent.KEYCODE_A&&kc<=KeyEvent.KEYCODE_Z) return 'A'+(kc-KeyEvent.KEYCODE_A);
        if (kc>=KeyEvent.KEYCODE_0&&kc<=KeyEvent.KEYCODE_9) return '0'+(kc-KeyEvent.KEYCODE_0);
        switch(kc){
            case KeyEvent.KEYCODE_SPACE: return ' ';
            case KeyEvent.KEYCODE_ENTER: return '\r';
            case KeyEvent.KEYCODE_DEL:   return 8;
            case KeyEvent.KEYCODE_DPAD_UP:    return 'W';
            case KeyEvent.KEYCODE_DPAD_DOWN:  return 'S';
            case KeyEvent.KEYCODE_DPAD_LEFT:  return 'A';
            case KeyEvent.KEYCODE_DPAD_RIGHT: return 'D';
        }
        return 0;
    }
}
