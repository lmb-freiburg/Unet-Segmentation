/**************************************************************************
 *
 * Copyright (C) 2015 Thorsten Falk
 *
 *        Image Analysis Lab, University of Freiburg, Germany
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301  USA
 *
 **************************************************************************/

// ImageJ stuff
import ij.*;
import ij.process.*;
import ij.gui.*;
import ij.plugin.frame.*;

// Java GUI stuff
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.*;
import java.util.*;

public class UnetJobManager extends PlugInFrame {

  private UnetJobTableModel _unetJobTableModel;
  private static UnetJobManager _instance = null;
  private static JDialog helpDialog = null;

  // This is a bad-style singleton with public constructor. It will
  // create new UnetJobManager objects for each plugin
  // call but in the run() method only the first is used. Actually
  // the constructor should be private and a static instance() method
  // should create a UnetJobManager on demand. The static
  // _instance reference will be valid as long as the virtual machine
  // is running.
  public UnetJobManager() {
    super("U-Net Job Manager");

    if (_instance != null) return;

    _unetJobTableModel = new UnetJobTableModel();

    setLayout(new GridBagLayout());

    JTable table = new JTable(_unetJobTableModel);
    table.getColumn("Status").setCellRenderer(
        new ProgressCellRenderer());
    table.getColumn("Progress").setCellRenderer(
        new ProgressCellRenderer());
    table.getColumn("Show").setCellRenderer(
        new ComponentCellRenderer());
    JButton terminatingButton = new JButton("Terminating...");
    table.getColumn("Show").setMinWidth(
        terminatingButton.getPreferredSize().width);
    table.getColumn("Show").setMaxWidth(
        terminatingButton.getPreferredSize().width);
    table.getColumn("Show").setPreferredWidth(
        terminatingButton.getPreferredSize().width);
    table.addMouseListener(new JTableButtonMouseListener(table));
    JScrollPane tableScroller = new JScrollPane(table);

    JButton newSegmentationJobButton = new JButton("New segmentation");
    newSegmentationJobButton.setToolTipText(
        "Segment the current image (stack) using U-Net");
    newSegmentationJobButton.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            _unetJobTableModel.createSegmentationJob(
                WindowManager.getCurrentImage());
          }});

    JButton newFinetuneJobButton = new JButton("Finetuning");
    newFinetuneJobButton.setToolTipText(
        "Finetune U-Net model to new data");
    newFinetuneJobButton.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            _unetJobTableModel.createFinetuneJob();
          }});

    JButton helpButton = new JButton("Help");
    helpButton.setToolTipText("Show README");
    helpButton.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            if (helpDialog == null) {
              helpDialog = new JDialog(
                  _instance, "U-Net Segmentation Help", false);
              helpDialog.getContentPane().setLayout(
                  new BorderLayout());
              JEditorPane helpPanel;
              try {
                helpPanel = new JEditorPane(
                    getClass().getResource("resources/README.html"));
              }
              catch (java.io.IOException e2) {
                helpPanel = new JEditorPane(
                    "text/html", "404 - Internal Resource not found");
              }
              helpPanel.setEditable(false);
              helpPanel.addHyperlinkListener(
                  new HyperlinkListener() {
                    public void hyperlinkUpdate(HyperlinkEvent e) {
                      if (e.getEventType() ==
                          HyperlinkEvent.EventType.ACTIVATED) {
                        if (Desktop.isDesktopSupported()) {
                          try {
                            Desktop.getDesktop().browse(e.getURL().toURI());
                          }
                          catch (java.net.URISyntaxException e2) {}
                          catch (java.io.IOException e2) {}
                        }
                      }
                    }});
              JScrollPane helpScroller = new JScrollPane(helpPanel);
              helpDialog.add(helpScroller, BorderLayout.CENTER);
              helpDialog.setMinimumSize(new Dimension(600, 400));
            }
            helpDialog.setVisible(true);
          }});

    GridBagConstraints c = new GridBagConstraints();
    c.weightx = 1;
    c.weighty = 1;
    c.fill = GridBagConstraints.BOTH;
    c.anchor = GridBagConstraints.CENTER;
    c.gridx = 0;
    c.gridy = 0;
    c.gridwidth = 3;
    add(tableScroller, c);
    c.weighty = 0;
    c.fill = GridBagConstraints.NONE;
    c.gridx = 0;
    c.gridy = 1;
    c.gridwidth = 1;
    c.anchor = GridBagConstraints.LAST_LINE_START;
    add(newSegmentationJobButton, c);
    c.gridx = 1;
    add(newFinetuneJobButton, c);
    c.gridx = 2;
    c.gridy = 1;
    c.anchor = GridBagConstraints.LAST_LINE_END;
    add(helpButton, c);

    setSize(getLayout().preferredLayoutSize(this));

    _instance = this;
  }

  @Override
  public void run(String arg) {
    _instance.setVisible(true);
  }

};

class ProgressCellRenderer extends JProgressBar implements TableCellRenderer {

  @Override
  public Component getTableCellRendererComponent(
      JTable table, Object value, boolean isSelected, boolean hasFocus,
      int row, int column) {
    if (value instanceof TaskStatus) {
      TaskStatus s = (TaskStatus) value;
      if (!getString().equals(s.name)) setString(s.name);
      if (getMaximum() != (int) s.maxProgress)
          setMaximum((int)s.maxProgress);
      if (getValue() != (int) s.progress) setValue((int) s.progress);
      if (isIndeterminate() != s.isIndeterminate)
          setIndeterminate(s.isIndeterminate);
      if (!isStringPainted()) setStringPainted(true);
    }

    if (value instanceof Float || value instanceof Integer) {
      int progress = 0;
      if (value instanceof Float)
          progress = Math.round(((Float) value) * 100f);
      else if (value instanceof Integer) progress = (Integer) value;
      if (getValue() != progress) setValue(progress);
      if (!isStringPainted()) setStringPainted(true);
    }

    return this;
  }

};

class ComponentCellRenderer implements TableCellRenderer {

  @Override
  public Component getTableCellRendererComponent(
      JTable table, Object value, boolean isSelected, boolean hasFocus,
      int row, int column) {
    if (value instanceof Component) return (Component)value;
    return null;
  }
};

class JTableButtonMouseListener implements MouseListener {
  private JTable _table;
  private int _row;
  private int _column;

  private boolean _forwardEventToButton(MouseEvent e) {
    TableColumnModel columnModel = _table.getColumnModel();
    _column = columnModel.getColumnIndexAtX(e.getX());
    _row    = e.getY() / _table.getRowHeight();

    Object value;
    JButton button;
    MouseEvent buttonEvent;

    if (_row >= _table.getRowCount() || _row < 0 ||
        _column >= _table.getColumnCount() || _column < 0) return false;

    value = _table.getValueAt(_row, _column);

    if(!(value instanceof JButton)) return false;

    button = (JButton)value;

    if (!button.isEnabled()) return false;

    buttonEvent =
        (MouseEvent)SwingUtilities.convertMouseEvent(_table, e, button);
    button.dispatchEvent(buttonEvent);

    // This is necessary so that when a button is pressed and released
    // it gets rendered properly.  Otherwise, the button may still appear
    // pressed down when it has been released.
    _table.repaint();

    return true;
  }

  public JTableButtonMouseListener(JTable table) {
    _table = table;
  }

  public void mouseClicked(MouseEvent e) {
    if (_forwardEventToButton(e)) {
      if (((UnetJobTableModel)_table.getModel()).getJob(
              (String)_table.getValueAt(_row, 0)).ready())
          ((UnetJobTableModel)_table.getModel()).showAndDequeueJob(
              (String)_table.getValueAt(_row, 0));
      else
          ((UnetJobTableModel)_table.getModel()).cancelJob(
              (String)_table.getValueAt(_row, 0));
    }
  }

  public void mouseEntered(MouseEvent e) {
    _forwardEventToButton(e);
  }

  public void mouseExited(MouseEvent e) {
    _forwardEventToButton(e);
  }

  public void mousePressed(MouseEvent e) {
    _forwardEventToButton(e);
  }

  public void mouseReleased(MouseEvent e) {
    _forwardEventToButton(e);
  }

};
