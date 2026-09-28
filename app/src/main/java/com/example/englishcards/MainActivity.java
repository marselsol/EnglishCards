package com.example.englishcards;

import android.app.*;
import android.os.Bundle;
import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import android.graphics.drawable.GradientDrawable;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
    private static final int REQ_IMPORT = 7001;
    private static final long MIN = 60_000L;
    private static final long DAY = 86_400_000L;

    private CardsDb db;
    private final ArrayList<Card> queue = new ArrayList<>();
    private Card current;
    private TextView wordView, translationView, statsView, emptyView;
    private Button revealButton, againButton, hardButton, goodButton, easyButton;
    private LinearLayout ratingRow, cardBox;
    private final SimpleDateFormat dayKey = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        db = new CardsDb(this);
        db.ensureSeeded();
        buildUi();
        loadQueue();
    }

    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + .5f); }

    private TextView text(String s, float sp, int color) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(sp); t.setTextColor(color);
        return t;
    }

    private GradientDrawable bg(int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color); g.setCornerRadius(dp((int)radius));
        return g;
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label); b.setTextAllCaps(false); b.setTextSize(13);
        b.setMinHeight(dp(50)); b.setPadding(dp(8), dp(6), dp(8), dp(6));
        return b;
    }

    private void buildUi() {
        getWindow().setStatusBarColor(Color.rgb(247,248,252));
        getWindow().setNavigationBarColor(Color.rgb(247,248,252));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(12), dp(18), dp(12));
        root.setBackgroundColor(Color.rgb(247,248,252));
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(dp(18), insets.getSystemWindowInsetTop()+dp(12), dp(18), insets.getSystemWindowInsetBottom()+dp(12));
            return insets;
        });

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("English Cards", 22, Color.rgb(32,36,48));
        title.setTypeface(Typeface.DEFAULT_BOLD);
        top.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1));
        Button importBtn = button("Импорт");
        Button settingsBtn = button("Настройки");
        top.addView(importBtn, new LinearLayout.LayoutParams(dp(92), dp(46)));
        top.addView(settingsBtn, new LinearLayout.LayoutParams(dp(112), dp(46)));
        root.addView(top);

        statsView = text("", 14, Color.rgb(95,103,118));
        statsView.setPadding(0, dp(4), 0, dp(12));
        root.addView(statsView);

        cardBox = new LinearLayout(this);
        cardBox.setOrientation(LinearLayout.VERTICAL);
        cardBox.setGravity(Gravity.CENTER);
        cardBox.setPadding(dp(22), dp(30), dp(22), dp(26));
        cardBox.setBackground(bg(Color.WHITE, 20));
        root.addView(cardBox, new LinearLayout.LayoutParams(-1, 0, 1));

        TextView hint = text("АНГЛИЙСКОЕ СЛОВО", 12, Color.rgb(145,151,164));
        hint.setGravity(Gravity.CENTER);
        cardBox.addView(hint);

        wordView = text("", 36, Color.rgb(28,32,42));
        wordView.setTypeface(Typeface.DEFAULT_BOLD);
        wordView.setGravity(Gravity.CENTER);
        wordView.setPadding(dp(8), dp(20), dp(8), dp(16));
        cardBox.addView(wordView);

        translationView = text("", 22, Color.rgb(58,66,82));
        translationView.setGravity(Gravity.CENTER);
        translationView.setPadding(dp(8), dp(10), dp(8), dp(16));
        cardBox.addView(translationView);

        emptyView = text("", 20, Color.rgb(58,66,82));
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setVisibility(View.GONE);
        cardBox.addView(emptyView, new LinearLayout.LayoutParams(-1, -2));

        revealButton = button("Показать перевод");
        revealButton.setTextSize(16);
        cardBox.addView(revealButton, new LinearLayout.LayoutParams(-1, dp(58)));

        ratingRow = new LinearLayout(this);
        ratingRow.setOrientation(LinearLayout.HORIZONTAL);
        ratingRow.setGravity(Gravity.CENTER);
        ratingRow.setVisibility(View.GONE);
        againButton = button("Снова"); hardButton = button("Трудно");
        goodButton = button("Хорошо"); easyButton = button("Легко");
        ratingRow.addView(againButton, new LinearLayout.LayoutParams(0, dp(62), 1));
        ratingRow.addView(hardButton, new LinearLayout.LayoutParams(0, dp(62), 1));
        ratingRow.addView(goodButton, new LinearLayout.LayoutParams(0, dp(62), 1));
        ratingRow.addView(easyButton, new LinearLayout.LayoutParams(0, dp(62), 1));
        cardBox.addView(ratingRow, new LinearLayout.LayoutParams(-1, -2));

        TextView footer = text("Снова — почти сразу • Трудно — скоро • Хорошо — обычный интервал • Легко — длиннее", 12, Color.rgb(125,132,146));
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(dp(8), dp(12), dp(8), 0);
        root.addView(footer);

        setContentView(root);

        revealButton.setOnClickListener(v -> reveal());
        againButton.setOnClickListener(v -> rate(0));
        hardButton.setOnClickListener(v -> rate(1));
        goodButton.setOnClickListener(v -> rate(2));
        easyButton.setOnClickListener(v -> rate(3));
        importBtn.setOnClickListener(v -> importCsv());
        settingsBtn.setOnClickListener(v -> showSettings());
    }

    private void loadQueue() {
        queue.clear();
        long now = System.currentTimeMillis();
        queue.addAll(db.dueCards(now, 250));
        int remainingNew = Math.max(0, getNewLimit() - newSeenToday());
        if (remainingNew > 0) queue.addAll(db.newCards(remainingNew));
        showNext();
    }

    private void showNext() {
        updateStats();
        if (queue.isEmpty()) {
            current = null;
            wordView.setVisibility(View.GONE);
            translationView.setVisibility(View.GONE);
            revealButton.setVisibility(View.GONE);
            ratingRow.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
            long next = db.nextDue();
            String s = "На сегодня всё!";
            if (next > System.currentTimeMillis()) s += "\n\nСледующее повторение: " + formatDue(next);
            emptyView.setText(s);
            return;
        }
        current = queue.remove(0);
        emptyView.setVisibility(View.GONE);
        wordView.setVisibility(View.VISIBLE);
        wordView.setText(current.word);
        translationView.setText(current.translation);
        translationView.setVisibility(View.GONE);
        revealButton.setVisibility(View.VISIBLE);
        ratingRow.setVisibility(View.GONE);
    }

    private void reveal() {
        if (current == null) return;
        translationView.setVisibility(View.VISIBLE);
        revealButton.setVisibility(View.GONE);
        ratingRow.setVisibility(View.VISIBLE);
        againButton.setText("Снова\n" + intervalLabel(0));
        hardButton.setText("Трудно\n" + intervalLabel(1));
        goodButton.setText("Хорошо\n" + intervalLabel(2));
        easyButton.setText("Легко\n" + intervalLabel(3));
    }

    private String intervalLabel(int grade) {
        if (current == null) return "";
        if (grade == 0) return "1 мин";
        if (grade == 1) return current.reps == 0 ? "10 мин" : prettyDays(Math.max(0.02, current.intervalDays * 1.2));
        if (grade == 2) return current.reps == 0 ? "1 д" : prettyDays(Math.max(1, current.intervalDays * current.ease));
        return current.reps == 0 ? "4 д" : prettyDays(Math.max(4, current.intervalDays * current.ease * 1.5));
    }

    private String prettyDays(double d) {
        if (d < 1.0/24) return Math.max(1, (int)Math.round(d*24*60)) + " мин";
        if (d < 1) return Math.max(1, (int)Math.round(d*24)) + " ч";
        if (d < 30) return Math.max(1, (int)Math.round(d)) + " д";
        if (d < 365) return Math.max(1, (int)Math.round(d/30)) + " мес";
        return String.format(Locale.US, "%.1f г", d/365.0);
    }

    private void rate(int grade) {
        if (current == null) return;
        boolean wasNew = current.isNew;
        long now = System.currentTimeMillis();
        double interval;
        double ease = current.ease <= 0 ? 2.5 : current.ease;
        long due;

        if (grade == 0) {
            interval = Math.max(1.0/1440.0, current.intervalDays * 0.15);
            ease = Math.max(1.30, ease - 0.20);
            due = now + MIN;
            current.lapses++;
        } else if (grade == 1) {
            if (current.reps == 0) { interval = 10.0/1440.0; due = now + 10*MIN; }
            else { interval = Math.max(1.0, current.intervalDays * 1.2); due = now + (long)(interval*DAY); }
            ease = Math.max(1.30, ease - 0.10);
        } else if (grade == 2) {
            interval = current.reps == 0 ? 1.0 : Math.max(1.0, current.intervalDays * ease);
            due = now + (long)(interval*DAY);
            ease = Math.min(3.5, ease + 0.03);
        } else {
            interval = current.reps == 0 ? 4.0 : Math.max(4.0, current.intervalDays * ease * 1.5);
            due = now + (long)(interval*DAY);
            ease = Math.min(3.5, ease + 0.15);
        }

        current.intervalDays = interval;
        current.ease = ease;
        current.reps++;
        current.isNew = false;
        current.due = due;
        db.saveProgress(current);
        if (wasNew) incrementNewSeenToday();

        if (grade == 0) queue.add(Math.min(2, queue.size()), current.copy());
        else if (grade == 1 && interval < 1) queue.add(Math.min(6, queue.size()), current.copy());
        showNext();
    }

    private void updateStats() {
        int due = db.countDue(System.currentTimeMillis());
        int seen = newSeenToday();
        int limit = getNewLimit();
        int total = db.countAll();
        int learned = db.countLearned();
        statsView.setText("Карточек: " + total + "   •   Изучено: " + learned + "   •   К повторению: " + due + "   •   Новых сегодня: " + seen + "/" + limit);
    }

    private int getNewLimit() { return getPreferences(MODE_PRIVATE).getInt("new_limit", 20); }
    private int newSeenToday() {
        SharedPreferences p = getPreferences(MODE_PRIVATE);
        String today = dayKey.format(new Date());
        if (!today.equals(p.getString("new_date", ""))) {
            p.edit().putString("new_date", today).putInt("new_seen", 0).apply();
            return 0;
        }
        return p.getInt("new_seen", 0);
    }
    private void incrementNewSeenToday() {
        SharedPreferences p = getPreferences(MODE_PRIVATE);
        String today = dayKey.format(new Date());
        int n = today.equals(p.getString("new_date", "")) ? p.getInt("new_seen", 0) : 0;
        p.edit().putString("new_date", today).putInt("new_seen", n+1).apply();
    }

    private void showSettings() {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(24), dp(8), dp(24), 0);
        TextView l = text("Новых слов в день", 14, Color.DKGRAY); box.addView(l);
        EditText input = new EditText(this); input.setInputType(2); input.setText(String.valueOf(getNewLimit())); box.addView(input);
        new AlertDialog.Builder(this)
                .setTitle("Настройки")
                .setView(box)
                .setPositiveButton("Сохранить", (d,w) -> {
                    try { int n = Math.max(1, Math.min(200, Integer.parseInt(input.getText().toString().trim()))); getPreferences(MODE_PRIVATE).edit().putInt("new_limit", n).apply(); loadQueue(); }
                    catch (Exception ignored) {}
                })
                .setNeutralButton("Сброс прогресса", (d,w) -> confirmReset())
                .setNegativeButton("Отмена", null).show();
    }

    private void confirmReset() {
        new AlertDialog.Builder(this).setTitle("Сбросить прогресс?")
                .setMessage("Все слова снова станут новыми. Импортированные слова останутся.")
                .setPositiveButton("Сбросить", (d,w) -> {
                    db.resetProgress();
                    getPreferences(MODE_PRIVATE).edit().remove("new_seen").remove("new_date").apply();
                    loadQueue();
                }).setNegativeButton("Отмена", null).show();
    }

    private void importCsv() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("text/*");
        startActivityForResult(i, REQ_IMPORT);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_IMPORT && resultCode == RESULT_OK && data != null && data.getData() != null) {
            int added = db.importCsv(data.getData());
            Toast.makeText(this, "Добавлено слов: " + added, Toast.LENGTH_LONG).show();
            loadQueue();
        }
    }

    private String formatDue(long t) {
        long diff = t - System.currentTimeMillis();
        if (diff < MIN) return "скоро";
        if (diff < DAY) return "через " + Math.max(1, diff/3_600_000L) + " ч";
        return new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(new Date(t));
    }

    static class Card {
        long id, due; String word, translation; double intervalDays, ease; int reps, lapses; boolean isNew;
        Card copy() { Card c = new Card(); c.id=id;c.due=due;c.word=word;c.translation=translation;c.intervalDays=intervalDays;c.ease=ease;c.reps=reps;c.lapses=lapses;c.isNew=isNew; return c; }
    }

    class CardsDb extends SQLiteOpenHelper {
        CardsDb(Context c) { super(c, "english_cards.db", null, 1); }
        @Override public void onCreate(SQLiteDatabase d) {
            d.execSQL("CREATE TABLE cards(id INTEGER PRIMARY KEY AUTOINCREMENT, word TEXT NOT NULL COLLATE NOCASE UNIQUE, translation TEXT NOT NULL, due INTEGER NOT NULL DEFAULT 0, interval_days REAL NOT NULL DEFAULT 0, ease REAL NOT NULL DEFAULT 2.5, reps INTEGER NOT NULL DEFAULT 0, lapses INTEGER NOT NULL DEFAULT 0, is_new INTEGER NOT NULL DEFAULT 1)");
            d.execSQL("CREATE INDEX idx_cards_due ON cards(is_new,due)");
        }
        @Override public void onUpgrade(SQLiteDatabase d, int o, int n) {}

        void ensureSeeded() {
            SQLiteDatabase d=getWritableDatabase();
            try(Cursor c=d.rawQuery("SELECT COUNT(*) FROM cards", null)) { c.moveToFirst(); if(c.getInt(0)>0) return; }
            d.beginTransaction();
            try(InputStream in=getAssets().open("words.csv"); BufferedReader r=new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line; while((line=r.readLine())!=null) addLine(d,line);
                d.setTransactionSuccessful();
            } catch(Exception e) { throw new RuntimeException(e); } finally { d.endTransaction(); }
        }

        private boolean addLine(SQLiteDatabase d, String line) {
            if(line==null) return false; line=line.trim(); if(line.isEmpty() || line.startsWith("#")) return false;
            String[] p=parseLine(line); if(p.length<2) return false;
            String en=p[0].trim(), ru=p[1].trim();
            if(en.equalsIgnoreCase("english") || en.equalsIgnoreCase("word")) return false;
            if(en.isEmpty()||ru.isEmpty()) return false;
            ContentValues v=new ContentValues(); v.put("word", en); v.put("translation", ru);
            return d.insertWithOnConflict("cards",null,v,SQLiteDatabase.CONFLICT_IGNORE)!=-1;
        }

        int importCsv(Uri uri) {
            int added=0; SQLiteDatabase d=getWritableDatabase(); d.beginTransaction();
            try(InputStream in=getContentResolver().openInputStream(uri); BufferedReader r=new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line; while((line=r.readLine())!=null) if(addLine(d,line)) added++;
                d.setTransactionSuccessful();
            } catch(Exception e) { Toast.makeText(MainActivity.this,"Ошибка импорта: "+e.getMessage(),Toast.LENGTH_LONG).show(); }
            finally { d.endTransaction(); }
            return added;
        }

        private String[] parseLine(String s) {
            char sep = s.indexOf('\t')>=0 ? '\t' : (s.indexOf(';')>=0 && s.indexOf(',')<0 ? ';' : ',');
            ArrayList<String> out=new ArrayList<>(); StringBuilder b=new StringBuilder(); boolean q=false;
            for(int i=0;i<s.length();i++) { char ch=s.charAt(i); if(ch=='\"') { if(q&&i+1<s.length()&&s.charAt(i+1)=='\"'){b.append('\"');i++;} else q=!q; } else if(ch==sep&&!q){out.add(b.toString());b.setLength(0);} else b.append(ch); }
            out.add(b.toString()); return out.toArray(new String[0]);
        }

        ArrayList<Card> dueCards(long now,int limit) { return query("SELECT * FROM cards WHERE is_new=0 AND due<=? ORDER BY due LIMIT "+limit,new String[]{String.valueOf(now)}); }
        ArrayList<Card> newCards(int limit) { return query("SELECT * FROM cards WHERE is_new=1 ORDER BY id LIMIT "+limit,null); }
        ArrayList<Card> query(String sql,String[] args) {
            ArrayList<Card> a=new ArrayList<>(); try(Cursor c=getReadableDatabase().rawQuery(sql,args)){ while(c.moveToNext()){Card x=new Card();x.id=c.getLong(c.getColumnIndexOrThrow("id"));x.word=c.getString(c.getColumnIndexOrThrow("word"));x.translation=c.getString(c.getColumnIndexOrThrow("translation"));x.due=c.getLong(c.getColumnIndexOrThrow("due"));x.intervalDays=c.getDouble(c.getColumnIndexOrThrow("interval_days"));x.ease=c.getDouble(c.getColumnIndexOrThrow("ease"));x.reps=c.getInt(c.getColumnIndexOrThrow("reps"));x.lapses=c.getInt(c.getColumnIndexOrThrow("lapses"));x.isNew=c.getInt(c.getColumnIndexOrThrow("is_new"))==1;a.add(x);} } return a;
        }
        void saveProgress(Card c) { ContentValues v=new ContentValues();v.put("due",c.due);v.put("interval_days",c.intervalDays);v.put("ease",c.ease);v.put("reps",c.reps);v.put("lapses",c.lapses);v.put("is_new",c.isNew?1:0);getWritableDatabase().update("cards",v,"id=?",new String[]{String.valueOf(c.id)}); }
        int countAll(){return scalar("SELECT COUNT(*) FROM cards",null);}
        int countLearned(){return scalar("SELECT COUNT(*) FROM cards WHERE is_new=0",null);}
        int countDue(long now){return scalar("SELECT COUNT(*) FROM cards WHERE is_new=0 AND due<=?",new String[]{String.valueOf(now)});}
        int scalar(String q,String[] a){try(Cursor c=getReadableDatabase().rawQuery(q,a)){c.moveToFirst();return c.getInt(0);}}
        long nextDue(){try(Cursor c=getReadableDatabase().rawQuery("SELECT MIN(due) FROM cards WHERE is_new=0 AND due>?",new String[]{String.valueOf(System.currentTimeMillis())})){c.moveToFirst();return c.isNull(0)?0:c.getLong(0);}}
        void resetProgress(){getWritableDatabase().execSQL("UPDATE cards SET due=0, interval_days=0, ease=2.5, reps=0, lapses=0, is_new=1");}
    }
}
