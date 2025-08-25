package com.johnaconda.pandora.attackcycle;

import net.runelite.client.ui.PluginPanel;

import javax.swing.*;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

import static com.johnaconda.pandora.attackcycle.DbModels.*;

public final class AttackCyclePanel extends PluginPanel implements DbService.Listener
{
    private final DbService db;
    private final AttackCyclePlugin plugin;

    public AttackCyclePanel(DbService db, AttackCyclePlugin plugin)
    {
        this.db = db;
        this.plugin = plugin;

        buildSidebarUI();
        wireSidebar();
        db.addListener(this);

        chkRecording.setSelected(plugin.isRecordingEnabled());
        chkLearner.setSelected(plugin.isAutoTagEnabled());
        chkExtended.setSelected(plugin.isDebugEnabled());

        refreshList();
    }

    // ===== Sidebar =====
    private final JCheckBox chkRecording = new JCheckBox("Recording");
    private final JCheckBox chkLearner   = new JCheckBox("Learner (auto-tag)");
    private final JCheckBox chkExtended  = new JCheckBox("Extended info");

    private final JTextField txtSearch   = new JTextField();
    private final JButton btnRefreshList = new JButton("Refresh");

    private final DefaultListModel<NpcProfile> listModel = new DefaultListModel<>();
    private final JList<NpcProfile> npcList = new JList<>(listModel);

    private final JButton btnAdvanced    = new JButton("Advanced…");
    private final JButton btnSaveAll     = new JButton("Save");
    private final JButton btnEraseAll    = new JButton("Erase database…");

    // ===== Advanced window =====
    private JFrame advancedFrame;
    private final JTabbedPane tabs = new JTabbedPane(); // [0]=Profile, [1]=Recording

    // Profile tab
    private JPanel profileRoot;
    private final JComboBox<NpcItem> comboNpcProfile = new JComboBox<>();
    private final JButton btnProfRefresh = new JButton("Refresh");
    private final JButton btnProfSave    = new JButton("Save");
    private final JButton btnProfRemove  = new JButton("Remove NPC…");

    private final JComboBox<String> comboPhase = new JComboBox<>();
    private final JButton btnAddPhase    = new JButton("Add Phase");
    private final JButton btnRemovePhase = new JButton("Remove Phase");

    private final JTextArea txtPhaseAnimTriggers = new JTextArea();
    private final JTextArea txtPhaseNpcTriggers  = new JTextArea();

    private final StyleBlockPanel panelMelee   = new StyleBlockPanel("Melee",  Style.MELEE);
    private final StyleBlockPanel panelRanged  = new StyleBlockPanel("Ranged", Style.RANGED);
    private final StyleBlockPanel panelMagic   = new StyleBlockPanel("Magic",  Style.MAGIC);
    private final StyleBlockPanel panelCharge  = new StyleBlockPanel("Charge-up", Style.UNKNOWN); // filtered by Type=CHARGEUP

    private final JCheckBox chkSmartUpdate = new JCheckBox("Smart update profile from tags");

    // Recording tab
    private JPanel recordingRoot;
    private final JComboBox<NpcItem> comboNpcRec = new JComboBox<>();
    private final JButton btnRecRefresh = new JButton("Refresh");
    private final JCheckBox chkRecAuto  = new JCheckBox("Auto-refresh");

    private final RecTableModel recModel = new RecTableModel();
    private final JTable recTable = new JTable(recModel);

    private final Timer recRefreshTimer = new Timer(200, e -> {
        if (isRecordingTabActive() && chkRecAuto.isSelected()) {
            refreshRecordingTable();
        }
    });
    { recRefreshTimer.setRepeats(false); }

    // ================= Sidebar =================
    private void buildSidebarUI()
    {
        setLayout(new BorderLayout());
        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBorder(new EmptyBorder(8,8,8,8));

        JPanel toggles = new JPanel(new GridLayout(0,1,0,4));
        toggles.add(chkRecording);
        toggles.add(chkLearner);
        toggles.add(chkExtended);
        root.add(toggles);
        root.add(Box.createVerticalStrut(8));

        JPanel searchRow = new JPanel(new BorderLayout(6,0));
        txtSearch.putClientProperty("JTextField.placeholderText", "Search NPCs…");
        searchRow.add(txtSearch, BorderLayout.CENTER);
        searchRow.add(btnRefreshList, BorderLayout.EAST);
        root.add(searchRow);
        root.add(Box.createVerticalStrut(8));

        npcList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        npcList.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object val, int index, boolean sel, boolean focus) {
                super.getListCellRendererComponent(list, val, index, sel, focus);
                if (val instanceof NpcProfile) {
                    NpcProfile p = (NpcProfile) val;
                    String name = (p.name == null ? "??" : p.name);
                    setText(name + " · " + p.level + "  " + p.variantIds);
                }
                return this;
            }
        });
        npcList.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2) openAdvanced(0);
            }
        });
        JPopupMenu menu = new JPopupMenu();
        JMenuItem miProfile = new JMenuItem("NPC profile…");
        miProfile.addActionListener(e -> openAdvanced(0));
        JMenuItem miRecs = new JMenuItem("Recorded data…");
        miRecs.addActionListener(e -> openAdvanced(1));
        JMenuItem miRelearn = new JMenuItem("Re-learn this NPC");
        miRelearn.addActionListener(e -> doRelearnSelected());
        JMenuItem miDelete = new JMenuItem("Remove NPC data…");
        miDelete.addActionListener(e -> doRemoveSelected());
        menu.add(miProfile); menu.add(miRecs); menu.addSeparator(); menu.add(miRelearn); menu.add(miDelete);
        npcList.setComponentPopupMenu(menu);

        root.add(new JScrollPane(npcList));
        root.add(Box.createVerticalStrut(8));

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT,6,0));
        bottom.add(btnSaveAll);
        bottom.add(btnAdvanced);
        bottom.add(btnEraseAll);
        root.add(bottom);

        add(root, BorderLayout.CENTER);
    }

    private void wireSidebar()
    {
        chkRecording.addActionListener(e -> plugin.setRecordingEnabled(chkRecording.isSelected()));
        chkLearner.addActionListener(e -> plugin.setAutoTagEnabled(chkLearner.isSelected()));
        chkExtended.addActionListener(e -> plugin.setDebugEnabled(chkExtended.isSelected()));

        btnRefreshList.addActionListener(e -> {
            refreshList();
            rebuildNpcCombos();
        });

        txtSearch.getDocument().addDocumentListener(new SimpleDocListener(this::refreshList));

        btnAdvanced.addActionListener(e -> openAdvanced(0));

        btnSaveAll.addActionListener(e -> {
            try { db.setHardOffline(false); db.save(); db.backupToFile(); }
            catch (Exception ignored) {}
            finally { db.setHardOffline(true); }
        });

        btnEraseAll.addActionListener(e -> {
            int res = JOptionPane.showConfirmDialog(this,
                    "This will erase ALL Attack Cycle profiles and recordings.\nAre you sure?",
                    "Erase database", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
            if (res == JOptionPane.OK_OPTION) {
                db.resetAll();
                refreshList();
                rebuildNpcCombos();
            }
        });
    }

    // ================= Advanced window =================
    private void openAdvanced(int tabIndex)
    {
        SwingUtilities.invokeLater(() -> {
            if (advancedFrame != null && advancedFrame.isShowing())
            {
                tabs.setSelectedIndex(tabIndex);
                advancedFrame.toFront();
                advancedFrame.requestFocus();
                return;
            }

            profileRoot = buildProfileTab();
            recordingRoot = buildRecordingTab();

            tabs.removeAll();
            tabs.addTab("Profile", new JScrollPane(profileRoot,
                    ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                    ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER));
            tabs.addTab("Recording", new JScrollPane(recordingRoot,
                    ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                    ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER));
            tabs.setSelectedIndex(tabIndex);

            tabs.addChangeListener(e -> requestRecordingRefresh());

            advancedFrame = new JFrame("Attack Cycle — Advanced");
            advancedFrame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            advancedFrame.setContentPane(tabs);
            advancedFrame.setSize(980, 900);
            advancedFrame.setLocationRelativeTo(null);
            advancedFrame.addWindowListener(new WindowAdapter() {
                @Override public void windowClosed(WindowEvent e) {
                    recRefreshTimer.stop();
                    advancedFrame = null;
                }
            });
            advancedFrame.getRootPane().registerKeyboardAction(
                    e -> advancedFrame.dispose(),
                    KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                    JComponent.WHEN_IN_FOCUSED_WINDOW
            );

            NpcProfile sel = selectedListProfile();
            rebuildNpcCombos();

            // compact: combos shouldn’t stretch tall
            comboNpcProfile.setPrototypeDisplayValue(new NpcItem("xxxxxxxx", "Some very long NPC name · 999"));
            comboNpcProfile.setPreferredSize(new Dimension(360, 26));
            comboNpcProfile.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
            comboNpcRec.setPrototypeDisplayValue(new NpcItem("xxxxxxxx", "Some very long NPC name · 999"));
            comboNpcRec.setPreferredSize(new Dimension(360, 26));
            comboNpcRec.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));

            if (sel != null) {
                selectNpcInCombo(comboNpcProfile, sel.key);
                selectNpcInCombo(comboNpcRec, sel.key);
            }

            loadSelectedProfile();
            refreshRecordingTable();

            advancedFrame.setVisible(true);
            advancedFrame.toFront();
            advancedFrame.requestFocus();
        });
    }

    // --- Profile tab
    private JPanel buildProfileTab()
    {
        JPanel root = new JPanel();
        root.setBorder(new EmptyBorder(8,8,8,8));
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));

        JPanel top = new JPanel(new BorderLayout(6,0));
        top.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32)); // keep short
        comboNpcProfile.setRenderer(new NpcItemRenderer());
        comboNpcProfile.addActionListener(e -> loadSelectedProfile());
        top.add(comboNpcProfile, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT,6,0));
        actions.add(btnProfRefresh);
        actions.add(btnProfSave);
        actions.add(btnProfRemove);
        top.add(actions, BorderLayout.EAST);

        root.add(top);
        root.add(Box.createVerticalStrut(6));

        btnProfRefresh.addActionListener(e -> loadSelectedProfile());
        btnProfSave.addActionListener(e -> saveProfileEdits());
        btnProfRemove.addActionListener(e -> {
            NpcItem it = (NpcItem) comboNpcProfile.getSelectedItem();
            if (it == null) return;
            int res = JOptionPane.showConfirmDialog(this,
                    "Remove all data for:\n" + it.label + " ?",
                    "Remove NPC", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
            if (res == JOptionPane.OK_OPTION) {
                db.deleteProfile(it.key);
                refreshList();
                rebuildNpcCombos();
                loadSelectedProfile();
            }
        });

        JPanel phaseRow = new JPanel(new FlowLayout(FlowLayout.LEFT,6,0));
        phaseRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        phaseRow.add(new JLabel("Phase:"));
        comboPhase.addActionListener(e -> loadPhaseFields());
        phaseRow.add(comboPhase);
        phaseRow.add(btnAddPhase);
        phaseRow.add(btnRemovePhase);
        root.add(phaseRow);
        root.add(Box.createVerticalStrut(6));

        // Triggers (side-by-side)
        JPanel triggers = new JPanel(new GridLayout(1,2,6,0));
        setupTriggerArea(txtPhaseAnimTriggers, "Trigger anim IDs (comma-separated):", triggers);
        setupTriggerArea(txtPhaseNpcTriggers,  "Trigger NPC variant IDs (comma-separated):", triggers);
        root.add(triggers);
        root.add(Box.createVerticalStrut(8));

        JPanel styles = new JPanel(new GridLayout(2,2,8,8));
        styles.add(panelMelee);
        styles.add(panelRanged);
        styles.add(panelMagic);
        styles.add(panelCharge);
        root.add(styles);

        root.add(Box.createVerticalStrut(8));
        chkSmartUpdate.addActionListener(e -> plugin.setAutoUpdateProfile(chkSmartUpdate.isSelected()));
        root.add(chkSmartUpdate);

        return root;
    }

    private void setupTriggerArea(JTextArea area, String title, JPanel container)
    {
        area.setRows(2);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        JPanel p = new JPanel(new BorderLayout(4,2));
        p.add(new JLabel(title), BorderLayout.NORTH);
        p.add(new JScrollPane(area,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER), BorderLayout.CENTER);
        container.add(p);
    }

    private void loadSelectedProfile()
    {
        NpcItem it = (NpcItem) comboNpcProfile.getSelectedItem();
        if (it == null) {
            comboPhase.removeAllItems();
            panelMelee.load(null, Map.of());
            panelRanged.load(null, Map.of());
            panelMagic.load(null, Map.of());
            panelCharge.load(null, Map.of());
            txtPhaseAnimTriggers.setText("");
            txtPhaseNpcTriggers.setText("");
            chkSmartUpdate.setSelected(false);
            return;
        }
        NpcProfile p = db.getProfileByKey(it.key);
        if (p == null) return;

        comboPhase.removeAllItems();
        comboPhase.addItem("base");
        for (String k : p.phases.keySet()) comboPhase.addItem(k);
        comboPhase.setSelectedIndex(0);

        Map<Integer, RecRow> recs = db.getRecsFor(p.key);

        panelMelee.load(p.base.melee,     recs);
        panelRanged.load(p.base.ranged,   recs);
        panelMagic.load(p.base.magic,     recs);
        panelCharge.load(p.base.chargeup, recs);

        loadPhaseFields();
        chkSmartUpdate.setSelected(plugin.isAutoUpdateProfile());
    }

    private void loadPhaseFields()
    {
        NpcItem it = (NpcItem) comboNpcProfile.getSelectedItem();
        if (it == null) return;
        NpcProfile p = db.getProfileByKey(it.key);
        if (p == null) return;

        Map<Integer, RecRow> recs = db.getRecsFor(p.key);
        String phase = (String) comboPhase.getSelectedItem();
        if (phase == null || "base".equals(phase))
        {
            txtPhaseAnimTriggers.setText("");
            txtPhaseNpcTriggers.setText("");
            panelMelee.load(p.base.melee,     recs);
            panelRanged.load(p.base.ranged,   recs);
            panelMagic.load(p.base.magic,     recs);
            panelCharge.load(p.base.chargeup, recs);
            return;
        }
        PhaseProfile ph = p.phases.get(phase);
        if (ph == null) return;

        txtPhaseAnimTriggers.setText(joinInts(ph.triggerAnimIds));
        txtPhaseNpcTriggers.setText(joinInts(ph.triggerNpcIds));

        panelMelee.load(ph.melee,     recs);
        panelRanged.load(ph.ranged,   recs);
        panelMagic.load(ph.magic,     recs);
        panelCharge.load(ph.chargeup, recs);
    }

    private void saveProfileEdits()
    {
        NpcProfile p = currentProfileOrNull();
        if (p == null) return;

        String phase = (String) comboPhase.getSelectedItem();
        PhaseProfile ph = "base".equals(phase) ? p.base : p.phases.get(phase);
        if (ph == null) {
            ph = new PhaseProfile();
            if (!"base".equals(phase)) p.phases.put(phase, ph);
        }

        panelMelee.storeInto(ph.melee);
        panelRanged.storeInto(ph.ranged);
        panelMagic.storeInto(ph.magic);
        panelCharge.storeInto(ph.chargeup);

        ph.triggerAnimIds = parseInts(txtPhaseAnimTriggers.getText());
        ph.triggerNpcIds  = parseInts(txtPhaseNpcTriggers.getText());

        db.putProfile(p);
        JOptionPane.showMessageDialog(this, "Profile saved.", "Attack Cycle", JOptionPane.INFORMATION_MESSAGE);
    }

    private NpcProfile currentProfileOrNull()
    {
        NpcItem it = (NpcItem) comboNpcProfile.getSelectedItem();
        if (it == null) return null;
        return db.getProfileByKey(it.key);
    }

    // --- Recording tab
    private JPanel buildRecordingTab()
    {
        JPanel root = new JPanel();
        root.setBorder(new EmptyBorder(8,8,8,8));
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));

        JPanel top = new JPanel(new BorderLayout(6,0));
        top.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        comboNpcRec.setRenderer(new NpcItemRenderer());
        comboNpcRec.addActionListener(e -> refreshRecordingTable());
        top.add(comboNpcRec, BorderLayout.CENTER);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT,6,0));
        chkRecAuto.setSelected(false);
        chkRecAuto.addActionListener(e -> requestRecordingRefresh());
        right.add(chkRecAuto);
        right.add(btnRecRefresh);
        btnRecRefresh.addActionListener(e -> refreshRecordingTable());
        top.add(right, BorderLayout.EAST);

        root.add(top);
        root.add(Box.createVerticalStrut(6));

        recTable.setFillsViewportHeight(true);
        recTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        recTable.setAutoCreateRowSorter(true);

        // context menu
        JPopupMenu menu = new JPopupMenu();
        JMenu setType = new JMenu("Set Type");
        for (AnimUiType t : AnimUiType.values()) {
            JMenuItem mi = new JMenuItem(t.name());
            mi.addActionListener(e -> recSetType(t));
            setType.add(mi);
        }
        JMenu setStyle = new JMenu("Set Style");
        for (Style s : Style.values()) {
            JMenuItem mi = new JMenuItem(s.name());
            mi.addActionListener(e -> recSetStyle(s));
            setStyle.add(mi);
        }
        JMenuItem miDelete = new JMenuItem("Delete row");
        miDelete.addActionListener(e -> recDeleteRow());
        JMenuItem miRelearn = new JMenuItem("Re-learn NPC");
        miRelearn.addActionListener(e -> {
            NpcItem it = (NpcItem) comboNpcRec.getSelectedItem();
            if (it != null) db.relearnNpc(it.key);
        });
        menu.add(setType); menu.add(setStyle); menu.addSeparator(); menu.add(miDelete); menu.add(miRelearn);
        recTable.setComponentPopupMenu(menu);

        root.add(new JScrollPane(recTable));
        return root;
    }

    private void requestRecordingRefresh()
    {
        if (!isRecordingTabActive() || !chkRecAuto.isSelected()) return;
        recRefreshTimer.restart();
    }

    private boolean isRecordingTabActive()
    {
        return advancedFrame != null && advancedFrame.isShowing() && tabs.getSelectedIndex() == 1;
    }

    private void refreshRecordingTable()
    {
        NpcItem it = (NpcItem) comboNpcRec.getSelectedItem();
        recModel.reload(db, (it != null ? it.key : null));
    }

    // ================= DbService.Listener =================
    @Override
    public void onDbChanged(DbService.Change change)
    {
        SwingUtilities.invokeLater(() -> {
            switch (change)
            {
                case RECORDING:
                    requestRecordingRefresh();
                    break;
                default:
                    refreshList();
                    rebuildNpcCombos();
                    loadSelectedProfile();
                    if (isRecordingTabActive()) refreshRecordingTable();
                    break;
            }
        });
    }

    // ================= Helpers =================
    private void refreshList()
    {
        List<NpcProfile> rows = db.searchProfiles(txtSearch.getText());
        listModel.clear();
        for (NpcProfile p : rows) listModel.addElement(p);
    }

    private NpcProfile selectedListProfile() { return npcList.getSelectedValue(); }

    private void doRelearnSelected()
    {
        NpcProfile p = selectedListProfile();
        if (p == null) return;
        int res = JOptionPane.showConfirmDialog(this,
                "Clear tags and estimates for:\n" + p.name + " · " + p.level + " ?",
                "Re-learn", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (res == JOptionPane.OK_OPTION) db.relearnNpc(p.key);
    }

    private void doRemoveSelected()
    {
        NpcProfile p = selectedListProfile();
        if (p == null) return;
        int res = JOptionPane.showConfirmDialog(this,
                "Remove ALL data for:\n" + p.name + " · " + p.level + " ?",
                "Remove NPC", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (res == JOptionPane.OK_OPTION) {
            db.deleteProfile(p.key);
            refreshList();
        }
    }

    private void rebuildNpcCombos()
    {
        Map<String, NpcProfile> map = db.getAllProfiles();
        List<NpcItem> items = map.values().stream()
                .sorted(Comparator.comparing((NpcProfile p) -> p.name).thenComparingInt(p -> p.level))
                .map(p -> new NpcItem(p.key, (p.name == null ? "??" : p.name) + " · " + p.level))
                .collect(Collectors.toList());

        NpcItem selProf = (NpcItem) comboNpcProfile.getSelectedItem();
        NpcItem selRec  = (NpcItem) comboNpcRec.getSelectedItem();

        comboNpcProfile.removeAllItems();
        comboNpcRec.removeAllItems();
        for (NpcItem it : items) { comboNpcProfile.addItem(it); comboNpcRec.addItem(it); }

        if (selProf != null) selectNpcInCombo(comboNpcProfile, selProf.key);
        if (selRec  != null) selectNpcInCombo(comboNpcRec, selRec.key);
    }

    private void selectNpcInCombo(JComboBox<NpcItem> combo, String key)
    {
        for (int i = 0; i < combo.getItemCount(); i++) {
            NpcItem it = combo.getItemAt(i);
            if (it != null && Objects.equals(it.key, key)) { combo.setSelectedIndex(i); return; }
        }
        if (combo.getItemCount() > 0) combo.setSelectedIndex(0);
    }

    private static String joinInts(Collection<Integer> ids)
    {
        if (ids == null || ids.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Integer i : ids) {
            if (i == null) continue;
            if (!first) sb.append(", ");
            sb.append(i);
            first = false;
        }
        return sb.toString();
    }

    private static Set<Integer> parseInts(String s)
    {
        Set<Integer> out = new LinkedHashSet<>();
        if (s == null || s.trim().isEmpty()) return out;
        String[] parts = s.split("[,\\s]+");
        for (String p : parts) {
            try { out.add(Integer.parseInt(p.trim())); } catch (Exception ignored) {}
        }
        return out;
    }

    private static final class NpcItem {
        final String key; final String label;
        NpcItem(String key, String label){ this.key=key; this.label=label; }
        @Override public String toString(){ return label; }
    }
    private static final class NpcItemRenderer extends DefaultListCellRenderer {
        @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof NpcItem) setText(((NpcItem) value).label);
            return this;
        }
    }

    private static final class SimpleDocListener implements DocumentListener {
        private final Runnable fn;
        SimpleDocListener(Runnable fn){ this.fn = fn; }
        private void fire(){ fn.run(); }
        @Override public void insertUpdate(DocumentEvent e){ fire(); }
        @Override public void removeUpdate(DocumentEvent e){ fire(); }
        @Override public void changedUpdate(DocumentEvent e){ fire(); }
    }

    // ---- Style block editor (per-anim) ----
    private final class StyleBlockPanel extends JPanel
    {
        private final DbModels.Style styleKind;
        private DbModels.StyleBlock boundBlock;
        private Map<Integer, DbModels.RecRow> recs = Map.of();

        private final JComboBox<AnimItem> cbAnim = new JComboBox<>();
        private final JSpinner spTicks  = new JSpinner(new SpinnerNumberModel(4, 0, 60, 1));
        private final JSpinner spOffset = new JSpinner(new SpinnerNumberModel(0, -8, 8, 1));
        private final JSpinner spProj   = new JSpinner(new SpinnerNumberModel(0, 0, 100, 1));

        StyleBlockPanel(String title, DbModels.Style styleKind)
        {
            this.styleKind = styleKind;
            setLayout(new GridBagLayout());
            setBorder(BorderFactory.createTitledBorder(title));

            GridBagConstraints c = new GridBagConstraints();
            c.insets = new Insets(2,2,2,2);
            c.anchor = GridBagConstraints.WEST;

            c.gridx=0; c.gridy=0; add(new JLabel("Animation:"), c);
            c.gridx=1; cbAnim.setPrototypeDisplayValue(new AnimItem(99999, "99999 MELEE (seen 99)"));
            cbAnim.setMaximumRowCount(12);
            cbAnim.setPreferredSize(new Dimension(220, 26));
            cbAnim.addActionListener(e -> loadSpinnersFromSelection());
            add(cbAnim, c);

            c.gridx=0; c.gridy=1; add(new JLabel("Ticks:"), c);
            c.gridx=1; add(spTicks, c);
            c.gridx=0; c.gridy=2; add(new JLabel("Offset:"), c);
            c.gridx=1; add(spOffset, c);
            c.gridx=0; c.gridy=3; add(new JLabel("Proj speed:"), c);
            c.gridx=1; add(spProj, c);
        }

        void load(DbModels.StyleBlock block, Map<Integer, DbModels.RecRow> recsForNpc)
        {
            this.boundBlock = block;
            this.recs = (recsForNpc == null ? Map.of() : recsForNpc);

            cbAnim.removeAllItems();
            final boolean isChargePanel = (styleKind == Style.UNKNOWN);

            for (DbModels.RecRow r : recs.values())
            {
                if (r == null) continue;
                if (isChargePanel) {
                    if (r.type != AnimUiType.CHARGEUP) continue; // only charge-ups
                } else {
                    if (r.type != AnimUiType.ATTACK) continue;
                    if (r.style != styleKind) continue;
                }
                String lbl = r.animId + " " + (isChargePanel ? "CHARGEUP" : r.style.name()) + " (seen " + r.seen + ")";
                cbAnim.addItem(new AnimItem(r.animId, lbl));
            }

            if (block != null && block.defaultAnimId != null) selectAnim(block.defaultAnimId);
            else if (cbAnim.getItemCount() > 0) cbAnim.setSelectedIndex(0);

            loadSpinnersFromSelection();
        }

        void storeInto(DbModels.StyleBlock block)
        {
            if (block == null) return;
            final AnimItem sel = (AnimItem) cbAnim.getSelectedItem();
            if (sel != null)
            {
                block.defaultAnimId = sel.id;
                DbModels.StyleAnimSettings s =
                        block.perAnim.computeIfAbsent(sel.id, k -> new DbModels.StyleAnimSettings());

                int ticks  = (int) spTicks.getValue();
                int offset = (int) spOffset.getValue();
                int proj   = (int) spProj.getValue();

                s.ticks     = (ticks  <= 0 ? null : ticks);
                s.offset    = offset;
                s.projSpeed = (proj   <= 0 ? null : proj);
            }
        }

        private void loadSpinnersFromSelection()
        {
            final AnimItem sel = (AnimItem) cbAnim.getSelectedItem();
            if (boundBlock == null || sel == null)
            {
                spTicks.setValue(4);
                spOffset.setValue(0);
                spProj.setValue(0);
                return;
            }
            final DbModels.StyleAnimSettings s = boundBlock.perAnim.get(sel.id);

            int ticks  = (s != null && s.ticks != null)     ? s.ticks     : (boundBlock.ticks != null ? boundBlock.ticks : 4);
            int offset = (s != null && s.offset != null)    ? s.offset    : (boundBlock.offset != null ? boundBlock.offset : 0);
            int proj   = (s != null && s.projSpeed != null) ? s.projSpeed : (boundBlock.projSpeed != null ? boundBlock.projSpeed : 0);

            spTicks.setValue(ticks);
            spOffset.setValue(offset);
            spProj.setValue(proj);
        }

        private void selectAnim(int id)
        {
            for (int i = 0; i < cbAnim.getItemCount(); i++)
            {
                AnimItem it = cbAnim.getItemAt(i);
                if (it != null && it.id == id) { cbAnim.setSelectedIndex(i); return; }
            }
        }
    }

    private static final class AnimItem {
        final int id; final String label;
        AnimItem(int id, String label){ this.id=id; this.label=label; }
        @Override public String toString(){ return label; }
    }

    // ---- Recording table
    private static final class RecRowView {
        int animId; AnimUiType type; Style style;
        int seen; Integer estTicks; Integer userTicks; Integer userProj; int lastSeenTick;
    }

    private final class RecTableModel extends AbstractTableModel
    {
        private final String[] COLS = {"Anim", "Type", "Style", "Seen", "EstTicks", "UserTicks", "UserProj", "LastSeen"};
        private final Class<?>[] TYPES = {Integer.class, String.class, String.class, Integer.class, Integer.class, Integer.class, Integer.class, Integer.class};
        private List<RecRowView> rows = new ArrayList<>();
        private String boundNpcKey = null;

        void reload(DbService db, String npcKey)
        {
            boundNpcKey = npcKey;
            rows = new ArrayList<>();
            if (npcKey != null) {
                Map<Integer, RecRow> recs = db.getRecsFor(npcKey);
                for (RecRow r : recs.values())
                {
                    RecRowView v = new RecRowView();
                    v.animId = r.animId;
                    v.type = (r.type == null ? AnimUiType.UNKNOWN : r.type);
                    v.style = (r.style == null ? Style.UNKNOWN : r.style);
                    v.seen = r.seen;
                    v.estTicks = r.liveEstTicksMedian();
                    v.userTicks = r.estTicksUser;
                    v.userProj = r.estProjSpeedUser;
                    v.lastSeenTick = r.lastSeenTick;
                    rows.add(v);
                }
                rows.sort(Comparator.comparingInt(a -> a.animId));
            }
            fireTableDataChanged();
        }

        @Override public int getRowCount(){ return rows.size(); }
        @Override public int getColumnCount(){ return COLS.length; }
        @Override public String getColumnName(int col){ return COLS[col]; }
        @Override public Class<?> getColumnClass(int c){ return TYPES[c]; }
        @Override public boolean isCellEditable(int r, int c){ return c==1 || c==2 || c==5 || c==6; }

        @Override public Object getValueAt(int r, int c)
        {
            RecRowView v = rows.get(r);
            switch (c) {
                case 0: return v.animId;
                case 1: return v.type.name();
                case 2: return v.style.name();
                case 3: return v.seen;
                case 4: return v.estTicks;
                case 5: return v.userTicks;
                case 6: return v.userProj;
                case 7: return v.lastSeenTick;
            }
            return null;
        }

        @Override public void setValueAt(Object aValue, int r, int c)
        {
            if (boundNpcKey == null) return;
            RecRowView v = rows.get(r);
            RecRow row = db.getOrCreateRec(boundNpcKey, v.animId);

            switch (c) {
                case 1: // type
                    try { v.type = AnimUiType.valueOf(aValue.toString()); }
                    catch (Exception ignored) { v.type = AnimUiType.UNKNOWN; }
                    row.type = v.type; db.putRec(boundNpcKey, row); break;
                case 2: // style
                    try { v.style = Style.valueOf(aValue.toString()); }
                    catch (Exception ignored) { v.style = Style.UNKNOWN; }
                    row.style = v.style; db.putRec(boundNpcKey, row); break;
                case 5: // user ticks
                    try { v.userTicks = (aValue == null ? null : Integer.valueOf(aValue.toString())); }
                    catch (Exception ex) { v.userTicks = null; }
                    row.estTicksUser = v.userTicks; db.putRec(boundNpcKey, row); break;
                case 6: // user proj
                    try { v.userProj = (aValue == null ? null : Integer.valueOf(aValue.toString())); }
                    catch (Exception ex) { v.userProj = null; }
                    row.estProjSpeedUser = v.userProj; db.putRec(boundNpcKey, row); break;
            }
            fireTableRowsUpdated(r, r);
        }

        void deleteRowAtSelection()
        {
            int viewRow = recTable.getSelectedRow();
            if (viewRow < 0 || boundNpcKey == null) return;
            int modelRow = recTable.convertRowIndexToModel(viewRow);
            RecRowView v = rows.get(modelRow);
            db.removeRec(boundNpcKey, v.animId);
            reload(db, boundNpcKey);
        }
    }

    private void recSetType(AnimUiType t)
    {
        int row = recTable.getSelectedRow();
        if (row < 0) return;
        int m = recTable.convertRowIndexToModel(row);
        recModel.setValueAt(t.name(), m, 1);
    }

    private void recSetStyle(Style s)
    {
        int row = recTable.getSelectedRow();
        if (row < 0) return;
        int m = recTable.convertRowIndexToModel(row);
        recModel.setValueAt(s.name(), m, 2);
    }

    private void recDeleteRow() { recModel.deleteRowAtSelection(); }
}
