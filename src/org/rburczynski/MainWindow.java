package org.rburczynski;

import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.Configuration;
import org.hibernate.metadata.ClassMetadata;
import org.hibernate.service.ServiceRegistry;

import javax.swing.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class MainWindow {

    private final AtomicBoolean running = new AtomicBoolean(false);

    private MainWindow() {
        comboBox.addActionListener(e -> {
            if(e.getActionCommand().equals("comboBoxChanged"))
                return;
            start();
        });
        button.setActionCommand("Start");
        button.addActionListener(e -> {
            if(e.getActionCommand().equals("Start"))
                start();
            else
                cancel();
        });
        linkList.addListSelectionListener(e -> {
            if(!linkList.isSelectionEmpty())
                comboBox.setSelectedItem(linkList.getSelectedValue());
        });
    }

    private static Session session;

    private void addEntry(String url){
        HistoryEntry entry = new HistoryEntry();
        entry.setDate(LocalDateTime.now());
        entry.setUrl(url);
        try {
            Transaction t = session.beginTransaction();
            MainWindow.session.persist(entry);
            t.commit();
            comboBox.insertItemAt(url, 0);
        } catch (Exception e) {
            //e.printStackTrace();
        }
    }

    public static MainWindow init() {
        JFrame frame = new JFrame("Main Window");
        Configuration conf = new Configuration();
        conf.configure();
        ServiceRegistry sr = new StandardServiceRegistryBuilder().applySettings(
                conf.getProperties()).build();
        session = new Configuration().configure().buildSessionFactory(sr).openSession();
        MainWindow win = new MainWindow();
        frame.setContentPane(win.panel);
        ClassMetadata meta = session.getSessionFactory().getClassMetadata(HistoryEntry.class);
        Query q = session.createQuery("from " + meta.getEntityName() + " order by date desc");
        for(Object o : q.list())
            win.comboBox.addItem(((HistoryEntry)o).getUrl());
        win.linkListModel = new DefaultListModel<>();
        win.linkList.setModel(win.linkListModel);
        win.imgListModel = new DefaultListModel<>();
        win.imgList.setModel(win.imgListModel);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.pack();
        frame.setVisible(true);
        return win;
    }

    private JProgressBar progressBar;
    private JComboBox<String> comboBox;
    private JLabel textLabel;
    private JPanel panel;
    private JButton button;
    private JList<String> linkList;
    private JList<String> imgList;
    private DefaultListModel<String> imgListModel;
    private DefaultListModel<String> linkListModel;

    private Worker worker;

    // this shouldn't be called from event dispatcher thread
    private void setLabel(String text){
        try{
            SwingUtilities.invokeAndWait(()->textLabel.setText(text));
        } catch(Exception ignored) {
        }
    }

    private void setProgress(int val){
        progressBar.setValue(val);
    }

    private void logImages(Collection<String> msgs){
        for(String msg : msgs)
            imgListModel.addElement(msg);
    }

    private void clearImgList(){
        imgListModel.clear();
    }

    private void clearLinkList() {
        linkListModel.clear();
    }

    private void logLinks(Collection<String> urls){
        for(String url : urls)
            linkListModel.addElement(url);
    }

    private void cancel(){
        if(worker != null) {
            worker.cancel(true);
            worker = null;
        }
    }

    private void start(){
        if(!running.compareAndSet(false, true))
            return;
        clearImgList();
        clearLinkList();
        button.setActionCommand("Cancel");
        button.setText("Cancel");
        worker = new Worker((String) comboBox.getSelectedItem());
        worker.execute();
    }

    public class Worker extends SwingWorker<Void, String> {

        private int finished = 0;
        private int total = 0;
        private final String url;
        public Worker(String s){
            url = s;
        }
        private boolean theEnd = false;

        @Override
        protected Void doInBackground() {
            try {
                setLabel("Connecting...");
                BufferedReader in;
                try {
                    in = Parser.getReader(url);
                } catch (IOException e) {
                    setLabel(e.getMessage());
                    return null;
                }
                setLabel("Connected");
                StringBuilder builder = new StringBuilder();
                String tmp;
                try {
                    while ((tmp = in.readLine()) != null)
                        builder.append(tmp);
                    in.close();
                } catch (IOException e) {
                    return null;
                }
                setLabel("Parsing HTML...");
                String html = builder.toString();
                Set<String> A = Parser.getLinks(html, url);
                Set<String> S = Parser.getImages(html, url);
                setLabel("" + S.size() + " images and " + A.size() + " links found.");
                try {
                    SwingUtilities.invokeAndWait(() -> logLinks(A));
                } catch(Exception ignored) {}
                total = S.size();
                final AtomicLong ans = new AtomicLong(0);
                addEntry(url);
                if(S.isEmpty())
                    return null;
                ExecutorService pool = Executors.newCachedThreadPool();
                for (String img : S)
                    pool.submit(() -> {
                        if (isCancelled())
                            return;
                        try {
                            long x = Parser.getImageSize(img);
                            ans.addAndGet(x);
                            publish(String.format("%s: %d b", img, x));
                        } catch (IOException e) {
                            publish(String.format("%s: %s", img, e.getMessage()));
                        }
                    });
                pool.shutdown();
                try {
                    pool.awaitTermination(9999999999L, TimeUnit.DAYS);
                } catch(InterruptedException e) {
                    pool.shutdownNow();
                    return null;
                }
                synchronized (this) {
                    while(!theEnd)
                        try {
                            wait();
                        } catch(InterruptedException ignored) {
                        }
                }
                try {
                    SwingUtilities.invokeAndWait(() ->
                            MainWindow.this.textLabel.setText(String.format("Total %d images; %.2f MB (%d bytes)", total, ans.get() / 1024.0 / 1024.0, ans.get())));
                } catch(Exception ignored){
                }
            } finally {
                running.set(false);
                try {
                    SwingUtilities.invokeAndWait(() -> {
                        button.setText("Start");
                        button.setActionCommand("Start");
                    });
                } catch (Exception ignored) {
                }
            }
            return null;
        }

        @Override
        protected void process(List<String> l) {
            MainWindow.this.logImages(l);
            finished += l.size();
            MainWindow.this.textLabel.setText("Processing: " + finished + "/" + total);
            MainWindow.this.setProgress(100 * finished / total);
            if(finished == total) {
                synchronized (this) {
                    theEnd = true;
                    this.notify();
                }
            }
        }
    }
}
