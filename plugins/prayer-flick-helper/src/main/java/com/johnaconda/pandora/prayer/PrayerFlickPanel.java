package com.johnaconda.pandora.prayer;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;

import net.runelite.client.ui.PluginPanel;

class PrayerFlickPanel extends PluginPanel
{
    interface Callbacks {
        void onToggleRecording(boolean enabled);
        void onSaveProfile(int npcId, String name, Map<Integer, LabelOption> proj, Map<Integer, AnimRow> anim);
        void onResetProfile(int npcId);
        void onAddNpcRequested();
        void onNpcSelected(Integer npcId);
        NpcProfile onRequestProfile(Integer npcId);

        // Auto-save hooks on cell edit:
        void onAnimRowEdited(int npcId, int animId, LabelOption label, RoleOption role, int offset);
        void onAnimRoleEdited(int npcId, int animId, RoleOption role);
        void onAnimOffsetEdited(int npcId, int animId, int offset);
        void onProjRowEdited(int npcId, int projId, LabelOption label);
    }

    static class AnimRow {
        final LabelOption label; final RoleOption role; final Integer period; final Integer offset;
        AnimRow(LabelOption l, RoleOption r, Integer p, Integer o){label=l;role=r;period=p;offset=o;}
    }

    private final JCheckBox recordingToggle = new JCheckBox("Recording");
    private final DefaultComboBoxModel<Integer> npcModel = new DefaultComboBoxModel<>();
    private final JComboBox<Integer> npcSelect = new JComboBox<>(npcModel);
    private final JTextField npcName = new JTextField();

    private final ProjModel projModel = new ProjModel();
    private final AnimModel animModel = new AnimModel();
    private final JTable projTable = new JTable(projModel);
    private final JTable animTable = new JTable(animModel);

    private Callbacks callbacks;

    PrayerFlickPanel() {
        setLayout(new BorderLayout(6, 6));

        // Top
        JPanel top = new JPanel(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(2,2,2,2);
        g.gridx=0; g.gridy=0; g.anchor=GridBagConstraints.WEST;
        top.add(new JLabel("NPC:"), g);
        g.gridx=1; g.fill=GridBagConstraints.HORIZONTAL; g.weightx=1.0;
        top.add(npcSelect, g);
        g.gridx=2; g.fill=GridBagConstraints.NONE; g.weightx=0;
        JButton addNpc = new JButton("New…"); top.add(addNpc, g);

        g.gridx=0; g.gridy=1;
        top.add(new JLabel("Name:"), g);
        g.gridx=1; g.gridwidth=2; g.fill=GridBagConstraints.HORIZONTAL; g.weightx=1.0;
        top.add(npcName, g);

        g.gridx=0; g.gridy=2; g.gridwidth=3; g.fill=GridBagConstraints.NONE; g.weightx=0;
        recordingToggle.addActionListener(e -> { if (callbacks != null) callbacks.onToggleRecording(recordingToggle.isSelected()); });
        top.add(recordingToggle, g);

        // Tables
        projTable.setRowHeight(22);
        animTable.setRowHeight(22);
        setUpEditors();

        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.add(title("Animations (set Role=Start for real attack anims; Period auto; flick≈Period-Offset when hit delay unknown)"));
        center.add(new JScrollPane(animTable));
        center.add(Box.createVerticalStrut(8));
        center.add(title("Projectiles"));
        center.add(new JScrollPane(projTable));

        // Buttons
        JPanel bottom = new JPanel(new GridLayout(1, 3, 6, 0));
        JButton save = new JButton("Save");
        JButton reset = new JButton("Reset NPC");
        JButton clear = new JButton("Clear rows");
        bottom.add(save); bottom.add(reset); bottom.add(clear);

        add(top, BorderLayout.NORTH);
        add(center, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);

        // Listeners
        addNpc.addActionListener(e -> { if (callbacks != null) callbacks.onAddNpcRequested(); });
        npcSelect.addActionListener(e -> {
            if (callbacks != null) callbacks.onNpcSelected(getSelectedNpc());
            loadFromStore();
        });
        save.addActionListener(e -> {
            Integer id = getSelectedNpc(); if (id == null || callbacks == null) return;
            callbacks.onSaveProfile(id, npcName.getText(), projModel.collect(), animModel.collect());
        });
        reset.addActionListener(e -> {
            Integer id = getSelectedNpc(); if (id != null && callbacks != null) callbacks.onResetProfile(id);
            loadFromStore();
        });
        clear.addActionListener(e -> { projModel.clear(); animModel.clear(); });
    }

    private Integer getSelectedNpc() { return (Integer) npcSelect.getSelectedItem(); }

    private void setUpEditors() {
        JComboBox<LabelOption> styleBox1 = new JComboBox<>(LabelOption.values());
        JComboBox<LabelOption> styleBox2 = new JComboBox<>(LabelOption.values());
        JComboBox<RoleOption> roleBox = new JComboBox<>(RoleOption.values());

        projTable.getColumnModel().getColumn(1).setCellEditor(new DefaultCellEditor(styleBox1));

        animTable.getColumnModel().getColumn(1).setCellEditor(new DefaultCellEditor(styleBox2)); // Label
        animTable.getColumnModel().getColumn(2).setCellEditor(new DefaultCellEditor(roleBox));   // Role

        // Offset editor as a spinner 0..4
        SpinnerNumberModel snm = new SpinnerNumberModel(1, 0, 4, 1);
        JSpinner spinner = new JSpinner(snm);
        animTable.getColumnModel().getColumn(4).setCellEditor(new DefaultCellEditor(new JTextField()) {
            @Override public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
                int v = 1;
                if (value instanceof String) try { v = Integer.parseInt((String)value.replaceAll("[^0-9-]","")); } catch (Exception ignored) {}
                if (value instanceof Integer) v = (Integer) value;
                ((SpinnerNumberModel)spinner.getModel()).setValue(Math.max(0, Math.min(4, v)));
                return spinner;
            }
            @Override public Object getCellEditorValue() { return ((SpinnerNumberModel)spinner.getModel()).getNumber().intValue(); }
        });

        projTable.putClientProperty("terminateEditOnFocusLost", true);
        animTable.putClientProperty("terminateEditOnFocusLost", true);
    }

    private static JLabel title(String t) {
        JLabel l = new JLabel(t);
        l.setFont(l.getFont().deriveFont(Font.BOLD));
        return l;
    }

    void setCallbacks(Callbacks c) {
        this.callbacks = c;
        projModel.parent = this;
        animModel.parent = this;
    }

    void setRecording(boolean on) { recordingToggle.setSelected(on); }

    void ensureNpcInList(int id) {
        for (int i=0;i<npcModel.getSize();i++) if (npcModel.getElementAt(i) == id) return;
        npcModel.addElement(id);
        if (npcModel.getSize()==1) npcSelect.setSelectedIndex(0);
    }

    void selectNpc(Integer id) {
        if (id == null) return;
        ensureNpcInList(id);
        npcSelect.setSelectedItem(id);
    }

    void setNpcName(String name) { npcName.setText(name == null ? "" : name); }

    // Called by plugin when unknown id is seen; only adds if absent
    void ensureAnimRow(int animId) { animModel.ensure(animId); }
    void ensureProjRow(int projId) { projModel.ensure(projId); }

    // Load current selection from store
    void loadFromStore() {
        if (callbacks == null) return;
        Integer id = getSelectedNpc();
        if (id == null) { projModel.clear(); animModel.clear(); npcName.setText(""); return; }

        NpcProfile p = callbacks.onRequestProfile(id);
        projModel.clear(); animModel.clear();
        if (p != null) {
            setNpcName(p.name);
            // Projectiles
            for (Map.Entry<Integer, AttackStyle> e : p.projectiles.entrySet()) {
                projModel.rows.put(e.getKey(), LabelOption.fromStyle(e.getValue()));
            }
            // Animations
            for (Map.Entry<Integer, NpcProfile.AnimInfo> e : p.animations.entrySet()) {
                LabelOption lab = LabelOption.fromStyle(e.getValue().style);
                RoleOption role = RoleOption.fromBool(e.getValue().start);
                Integer period = e.getValue().periodTicks;
                Integer offset = e.getValue().offset;
                animModel.rows.put(e.getKey(), new AnimRow(lab, role, period, offset));
            }
            projModel.fireTableDataChanged();
            animModel.fireTableDataChanged();
        }
    }

    // ---- table models ----
    private static class ProjModel extends AbstractTableModel {
        final Map<Integer, LabelOption> rows = new LinkedHashMap<>();
        PrayerFlickPanel parent;

        void ensure(int id){
            if (!rows.containsKey(id)) {
                rows.put(id, LabelOption.NONE);
                fireTableDataChanged();
            }
        }
        void clear(){ rows.clear(); fireTableDataChanged(); }
        Map<Integer, LabelOption> collect(){ return new LinkedHashMap<>(rows); }
        @Override public int getRowCount(){ return rows.size(); }
        @Override public int getColumnCount(){ return 2; }
        @Override public String getColumnName(int c){ return c==0?"Projectile ID":"Label"; }
        @Override public Object getValueAt(int r,int c){
            Integer id=(Integer)rows.keySet().toArray()[r];
            return c==0?id:rows.get(id);
        }
        @Override public boolean isCellEditable(int r,int c){ return c==1; }
        @Override public void setValueAt(Object v,int r,int c){
            if(c!=1) return;
            Integer id=(Integer)rows.keySet().toArray()[r];
            LabelOption lab=(LabelOption)v;
            rows.put(id, lab);
            fireTableCellUpdated(r,c);
            if (parent != null && parent.callbacks != null && parent.getSelectedNpc()!=null) {
                parent.callbacks.onProjRowEdited(parent.getSelectedNpc(), id, lab);
            }
        }
    }

    private static class AnimModel extends AbstractTableModel {
        final Map<Integer, AnimRow> rows = new LinkedHashMap<>();
        PrayerFlickPanel parent;

        void ensure(int id){
            if (!rows.containsKey(id)) {
                rows.put(id, new AnimRow(LabelOption.NONE, RoleOption.OTHER, null, 1));
                fireTableDataChanged();
            }
        }
        void clear(){ rows.clear(); fireTableDataChanged(); }
        Map<Integer, AnimRow> collect(){ return new LinkedHashMap<>(rows); }
        @Override public int getRowCount(){ return rows.size(); }
        @Override public int getColumnCount(){ return 5; }
        @Override public String getColumnName(int c){ return new String[]{"Anim ID","Label","Role","Period","Offset"}[c]; }
        @Override public Object getValueAt(int r,int c){
            Integer id=(Integer)rows.keySet().toArray()[r];
            AnimRow ar=rows.get(id);
            if (c==0) return id;
            if (c==1) return ar.label;
            if (c==2) return ar.role;
            if (c==3) return (ar.period == null ? "—" : ar.period + "t");
            return ar.offset == null ? 1 : ar.offset;
        }
        @Override public boolean isCellEditable(int r,int c){ return c==1 || c==2 || c==4; }
        @Override public void setValueAt(Object v,int r,int c){
            Integer id=(Integer)rows.keySet().toArray()[r];
            AnimRow ar=rows.get(id);
            if (c==1) {
                LabelOption lab=(LabelOption)v;
                rows.put(id, new AnimRow(lab, ar.role, ar.period, ar.offset));
                fireTableCellUpdated(r,c);
                if (parent != null && parent.callbacks != null && parent.getSelectedNpc()!=null) {
                    parent.callbacks.onAnimRowEdited(parent.getSelectedNpc(), id, lab, ar.role, ar.offset==null?1:ar.offset);
                }
            } else if (c==2) {
                RoleOption role=(RoleOption)v;
                rows.put(id, new AnimRow(ar.label, role, ar.period, ar.offset));
                fireTableCellUpdated(r,c);
                if (parent != null && parent.callbacks != null && parent.getSelectedNpc()!=null) {
                    parent.callbacks.onAnimRoleEdited(parent.getSelectedNpc(), id, role);
                }
            } else if (c==4) {
                int off = 1;
                if (v instanceof Integer) off = (Integer) v;
                if (v instanceof String) try { off = Integer.parseInt(((String)v).trim()); } catch (Exception ignored) {}
                off = Math.max(0, Math.min(4, off));
                rows.put(id, new AnimRow(ar.label, ar.role, ar.period, off));
                fireTableCellUpdated(r,c);
                if (parent != null && parent.callbacks != null && parent.getSelectedNpc()!=null) {
                    parent.callbacks.onAnimOffsetEdited(parent.getSelectedNpc(), id, off);
                }
            }
        }
    }
}
