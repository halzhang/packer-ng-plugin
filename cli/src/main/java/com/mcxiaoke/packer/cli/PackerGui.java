package com.mcxiaoke.packer.cli;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PackerGui extends JFrame {

    private JTextField apkField;
    private JTextField outputField;
    private JTextField channelsField;
    private JTextArea logArea;
    private JButton packButton;
    private JProgressBar progressBar;
    private JLabel statusLabel;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "packer-worker");
        t.setDaemon(true);
        return t;
    });

    public PackerGui() {
        super("APK 渠道打包工具");
        buildUI();
        pack();
        setMinimumSize(new Dimension(720, 580));
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private void buildUI() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JPanel root = new JPanel(new BorderLayout(0, 8));
        root.setBorder(BorderFactory.createEmptyBorder(12, 14, 8, 14));
        root.add(buildFormPanel(), BorderLayout.NORTH);
        root.add(buildLogPanel(), BorderLayout.CENTER);
        root.add(buildStatusBar(), BorderLayout.SOUTH);
        setContentPane(root);
    }

    private JPanel buildFormPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("打包配置"));

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(5, 8, 5, 8);
        c.fill = GridBagConstraints.HORIZONTAL;

        // APK row
        c.gridx = 0; c.gridy = 0; c.weightx = 0;
        panel.add(new JLabel("主包 APK:"), c);
        apkField = new JTextField();
        c.gridx = 1; c.weightx = 1.0;
        panel.add(apkField, c);
        JButton apkBtn = new JButton("浏览…");
        c.gridx = 2; c.weightx = 0;
        panel.add(apkBtn, c);

        // Output dir row
        c.gridx = 0; c.gridy = 1; c.weightx = 0;
        panel.add(new JLabel("输出目录:"), c);
        outputField = new JTextField();
        c.gridx = 1; c.weightx = 1.0;
        panel.add(outputField, c);
        JPanel outBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        JButton outBtn = new JButton("浏览…");
        JButton openBtn = new JButton("打开");
        outBtns.add(outBtn);
        outBtns.add(openBtn);
        c.gridx = 2; c.weightx = 0;
        panel.add(outBtns, c);

        // Channels row
        c.gridx = 0; c.gridy = 2; c.weightx = 0;
        panel.add(new JLabel("渠道号:"), c);
        channelsField = new JTextField();
        channelsField.setToolTipText("多个渠道号用英文逗号分隔，例如：xiaomi,huawei,baidu");
        c.gridx = 1; c.weightx = 1.0;
        panel.add(channelsField, c);
        JLabel hint = new JLabel("多个渠道用英文逗号分隔");
        hint.setForeground(Color.GRAY);
        hint.setFont(hint.getFont().deriveFont(11f));
        c.gridx = 2; c.weightx = 0;
        panel.add(hint, c);

        // Button row
        packButton = new JButton("  开始打包  ");
        packButton.setFont(packButton.getFont().deriveFont(Font.BOLD, 13f));
        packButton.setPreferredSize(new Dimension(130, 34));
        JButton clearBtn = new JButton("清空日志");
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 8));
        btnRow.add(packButton);
        btnRow.add(clearBtn);
        c.gridx = 0; c.gridy = 3; c.gridwidth = 3; c.weightx = 1.0;
        panel.add(btnRow, c);

        // Listeners
        apkBtn.addActionListener(e -> chooseApk());
        outBtn.addActionListener(e -> chooseOutputDir());
        openBtn.addActionListener(e -> {
            String path = outputField.getText().trim();
            if (!path.isEmpty()) openFolder(new File(path));
        });
        packButton.addActionListener(e -> startPacking());
        clearBtn.addActionListener(e -> logArea.setText(""));
        setupDrop(apkField);
        return panel;
    }

    private JPanel buildLogPanel() {
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        logArea.setBackground(new Color(28, 28, 28));
        logArea.setForeground(new Color(210, 210, 210));
        logArea.setCaretColor(Color.WHITE);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);

        JScrollPane scroll = new JScrollPane(logArea);
        scroll.setBorder(BorderFactory.createTitledBorder("运行日志"));
        scroll.setPreferredSize(new Dimension(0, 280));

        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setString("");

        JButton saveBtn = new JButton("另存日志…");
        saveBtn.addActionListener(e -> saveLogAs());

        JPanel bottom = new JPanel(new BorderLayout(6, 0));
        bottom.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        bottom.add(progressBar, BorderLayout.CENTER);
        bottom.add(saveBtn, BorderLayout.EAST);

        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.add(scroll, BorderLayout.CENTER);
        panel.add(bottom, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildStatusBar() {
        statusLabel = new JLabel("就绪");
        statusLabel.setForeground(Color.GRAY);
        statusLabel.setFont(statusLabel.getFont().deriveFont(11f));
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 2));
        bar.add(statusLabel);
        return bar;
    }

    private void chooseApk() {
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new FileNameExtensionFilter("APK 文件 (*.apk)", "apk"));
        if (!apkField.getText().isEmpty())
            fc.setCurrentDirectory(new File(apkField.getText()).getParentFile());
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            apkField.setText(fc.getSelectedFile().getAbsolutePath());
            if (outputField.getText().isEmpty()) {
                outputField.setText(
                    fc.getSelectedFile().getParent() + File.separator + "packer_output");
            }
        }
    }

    private void chooseOutputDir() {
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (!outputField.getText().isEmpty())
            fc.setCurrentDirectory(new File(outputField.getText()));
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
            outputField.setText(fc.getSelectedFile().getAbsolutePath());
    }

    private void setupDrop(JTextField field) {
        new DropTarget(field, DnDConstants.ACTION_COPY, new DropTargetAdapter() {
            @Override
            public void drop(DropTargetDropEvent e) {
                try {
                    e.acceptDrop(DnDConstants.ACTION_COPY);
                    @SuppressWarnings("unchecked")
                    List<File> files = (List<File>)
                        e.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    if (!files.isEmpty() && files.get(0).getName().endsWith(".apk")) {
                        field.setText(files.get(0).getAbsolutePath());
                        if (outputField.getText().isEmpty()) {
                            outputField.setText(
                                files.get(0).getParent() + File.separator + "packer_output");
                        }
                    }
                } catch (Exception ex) { /* ignore */ }
            }
        });
    }

    private void startPacking() {
        String apkPath   = apkField.getText().trim();
        String outPath   = outputField.getText().trim();
        String chText    = channelsField.getText().trim();

        if (apkPath.isEmpty())  { warn("请选择主包 APK 文件"); return; }
        if (outPath.isEmpty())  { warn("请选择输出目录"); return; }
        if (chText.isEmpty())   { warn("请输入渠道号"); return; }

        File apkFile = new File(apkPath);
        if (!apkFile.exists())  { warn("APK 文件不存在:\n" + apkPath); return; }

        Set<String> channels = Helper.parseChannels(chText);
        if (channels.isEmpty()) { warn("未解析到有效渠道号，请检查输入"); return; }

        File outDir = new File(outPath);
        outDir.mkdirs();

        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File logFile = new File(outDir, "packer-ng-" + ts + ".log");

        packButton.setEnabled(false);
        progressBar.setMaximum(channels.size());
        progressBar.setValue(0);
        progressBar.setString("准备中…");
        setStatus("打包中…", Color.GRAY);

        executor.submit(() -> {
            StringBuilder logBuf = new StringBuilder();
            String startTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            log(logBuf, "========== APK 渠道打包工具 ==========");
            log(logBuf, "开始时间: " + startTime);
            log(logBuf, "主  包: " + apkPath);
            log(logBuf, "输出目录: " + outPath);
            log(logBuf, "渠道数量: " + channels.size() + " 个");
            log(logBuf, "渠道列表: " + chText.trim());
            log(logBuf, "--------------------------------------");

            // Verify APK signature
            log(logBuf, "验证主包签名…");
            try {
                if (!Bridge.verifyApk(apkFile)) {
                    log(logBuf, "✗ 主包签名无效（需要 V2 签名）");
                    writeLogFile(logFile, logBuf);
                    SwingUtilities.invokeLater(() -> {
                        packButton.setEnabled(true);
                        progressBar.setString("失败");
                        setStatus("主包签名验证失败", Color.RED);
                    });
                    return;
                }
            } catch (Exception ex) {
                log(logBuf, "✗ 签名验证异常: " + ex.getMessage());
                writeLogFile(logFile, logBuf);
                SwingUtilities.invokeLater(() -> {
                    packButton.setEnabled(true);
                    progressBar.setString("失败");
                    setStatus("签名验证异常", Color.RED);
                });
                return;
            }
            log(logBuf, "✓ 签名验证通过");

            String baseName = Helper.getBaseName(apkFile.getName());
            String extName  = Helper.getExtName(apkFile.getName());

            int success = 0, fail = 0, idx = 0;
            for (String channel : channels) {
                idx++;
                final int cur = idx;
                final String ch = channel.trim();
                SwingUtilities.invokeLater(() -> {
                    progressBar.setValue(cur);
                    progressBar.setString(
                        String.format("打包 %d/%d：%s", cur, channels.size(), ch));
                });

                String apkName = String.format(java.util.Locale.US,
                    "%s-%s.%s", baseName, ch, extName);
                File dest = new File(outDir, apkName);
                try {
                    Helper.copyFile(apkFile, dest);
                    Bridge.writeChannel(dest, ch);
                    if (Bridge.verifyChannel(dest, ch)) {
                        log(logBuf, "✓ " + apkName);
                        success++;
                    } else {
                        dest.delete();
                        log(logBuf, "✗ [" + ch + "] 渠道写入验证失败");
                        fail++;
                    }
                } catch (Exception ex) {
                    if (dest.exists()) dest.delete();
                    log(logBuf, "✗ [" + ch + "] 异常: " + ex.getMessage());
                    fail++;
                }
            }

            String endTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            log(logBuf, "--------------------------------------");
            log(logBuf, "完成时间: " + endTime);
            log(logBuf, String.format("打包结果: 成功 %d 个，失败 %d 个", success, fail));
            log(logBuf, "日志文件: " + logFile.getAbsolutePath());
            log(logBuf, "======================================");
            writeLogFile(logFile, logBuf);

            final int s = success, f = fail;
            final File finalOutDir = outDir;
            SwingUtilities.invokeLater(() -> {
                packButton.setEnabled(true);
                progressBar.setValue(channels.size());
                String result = String.format("完成：成功 %d 个，失败 %d 个", s, f);
                progressBar.setString(result);
                setStatus(result, f == 0 ? new Color(0, 140, 0) : Color.RED);

                if (f == 0) {
                    int choice = JOptionPane.showOptionDialog(this,
                        String.format("打包完成！共生成 %d 个渠道包。\n\n输出目录：%s",
                            s, finalOutDir.getAbsolutePath()),
                        "打包成功",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.INFORMATION_MESSAGE,
                        null,
                        new String[]{"打开输出目录", "关闭"},
                        "打开输出目录");
                    if (choice == JOptionPane.YES_OPTION) {
                        openFolder(finalOutDir);
                    }
                } else {
                    JOptionPane.showMessageDialog(this,
                        String.format("打包结束。成功 %d 个，失败 %d 个。\n请查看日志了解失败详情。", s, f),
                        "打包完成（有失败）",
                        JOptionPane.WARNING_MESSAGE);
                }
            });
        });
    }

    private void openFolder(File dir) {
        try {
            Desktop.getDesktop().open(dir);
        } catch (Exception ex) {
            // fallback: use explorer directly
            try {
                Runtime.getRuntime().exec("explorer.exe \"" + dir.getAbsolutePath() + "\"");
            } catch (Exception ignored) {}
        }
    }

    private void log(StringBuilder buf, String msg) {
        buf.append(msg).append("\n");
        SwingUtilities.invokeLater(() -> {
            logArea.append(msg + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private void writeLogFile(File file, StringBuilder content) {
        try (PrintWriter pw = new PrintWriter(
                new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            pw.print(content);
        } catch (IOException ex) {
            SwingUtilities.invokeLater(() ->
                logArea.append("⚠ 日志文件保存失败: " + ex.getMessage() + "\n"));
        }
    }

    private void saveLogAs() {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("packer-ng-" +
            new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".log"));
        fc.setFileFilter(new FileNameExtensionFilter("日志文件 (*.log)", "log"));
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(
                    new FileOutputStream(fc.getSelectedFile()), StandardCharsets.UTF_8))) {
                pw.print(logArea.getText());
                JOptionPane.showMessageDialog(this,
                    "日志已保存到:\n" + fc.getSelectedFile().getAbsolutePath());
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "保存失败: " + ex.getMessage(),
                    "错误", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void setStatus(String msg, Color color) {
        statusLabel.setText(msg);
        statusLabel.setForeground(color);
    }

    private void warn(String msg) {
        JOptionPane.showMessageDialog(this, msg, "提示", JOptionPane.WARNING_MESSAGE);
    }

    public static void launch() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}
        SwingUtilities.invokeLater(PackerGui::new);
    }
}
