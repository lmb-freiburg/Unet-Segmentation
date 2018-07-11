/**************************************************************************
 *
 * Copyright (C) 2018 Thorsten Falk
 *
 *        Image Analysis Lab, University of Freiburg, Germany
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 **************************************************************************/

package de.unifreiburg.unet;

import java.awt.Component;
import java.awt.event.KeyListener;
import java.awt.event.KeyEvent;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DragSourceListener;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragSourceDragEvent;
import java.awt.dnd.DragSourceEvent;
import java.awt.dnd.DragSourceDropEvent;
import java.awt.dnd.DragGestureRecognizer;
import java.awt.dnd.DnDConstants;

import java.io.IOException;

import java.util.List;

import javax.swing.JList;
import javax.swing.DefaultListModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.TransferHandler;
import javax.swing.ListSelectionModel;
import javax.swing.DropMode;

public class TrainImagePairListView extends JList<TrainImagePair> {

  private DefaultListModel<TrainImagePair> model;

  public TrainImagePairListView() {
    super(new DefaultListModel<TrainImagePair>());
    model = (DefaultListModel<TrainImagePair>) getModel();
    setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    setDragEnabled(true);
    setDropMode(DropMode.INSERT);
    setTransferHandler(new TrainImagePairListViewDropHandler(this));
    setCellRenderer(new TrainImagePairListViewNameRenderer());
    new TrainImagePairListViewDragListener(this);
    addKeyListener(new TrainImagePairListViewKeyListener(this));
  }

}


class TrainImagePairListViewNameRenderer extends DefaultListCellRenderer {

  public TrainImagePairListViewNameRenderer() {
    setOpaque(true);
  }

  @Override
  public Component getListCellRendererComponent(
      JList<?> list, Object imgPair, int index,
      boolean isSelected, boolean cellHasFocus) {
    setText(((TrainImagePair)imgPair).toString());
    if (isSelected) {
      setBackground(list.getSelectionBackground());
      setForeground(list.getSelectionForeground());
    } else {
      setBackground(list.getBackground());
      setForeground(list.getForeground());
    }
    return this;
  }
}


class TrainImagePairListViewKeyListener implements KeyListener
{

  private TrainImagePairListView _list;

  public TrainImagePairListViewKeyListener(TrainImagePairListView list) {
    this._list = list;
  }

  public void keyPressed(KeyEvent e) {
    if (e.getKeyCode() == KeyEvent.VK_DELETE)
        for (TrainImagePair imgPair :
                 (List<TrainImagePair>)_list.getSelectedValuesList())
            ((DefaultListModel)_list.getModel()).removeElement(imgPair);
  }

  public void keyReleased(KeyEvent e) {}

  public void keyTyped(KeyEvent e) {}

}


class TransferableTrainImagePair implements Transferable {

  public static final DataFlavor TRAIN_IMAGE_PAIR_FLAVOR =
      new DataFlavor(TrainImagePair.class, "java/TrainImagePair");

  private TrainImagePair _imgPair;

  public TransferableTrainImagePair(TrainImagePair imgPair) {
    this._imgPair = imgPair;
  }

  @Override
  public DataFlavor[] getTransferDataFlavors() {
    return new DataFlavor[]{TRAIN_IMAGE_PAIR_FLAVOR};
  }

  @Override
  public boolean isDataFlavorSupported(DataFlavor flavor) {
    return flavor.equals(TRAIN_IMAGE_PAIR_FLAVOR);
  }

  @Override
  public Object getTransferData(DataFlavor flavor)
      throws UnsupportedFlavorException, IOException {
    return _imgPair;
  }
}



class TrainImagePairListViewDragListener
    implements DragSourceListener, DragGestureListener {
  private TrainImagePairListView _list;
  private TrainImagePair _imgPair = null;
  private DragSource _ds = new DragSource();

  public TrainImagePairListViewDragListener(TrainImagePairListView list) {
    this._list = list;
    DragGestureRecognizer dgr =
        _ds.createDefaultDragGestureRecognizer(
            _list, DnDConstants.ACTION_MOVE, this);
  }

  public void dragGestureRecognized(DragGestureEvent dge) {
    _imgPair = _list.getSelectedValue();
    _ds.startDrag(dge, DragSource.DefaultMoveDrop,
                  new TransferableTrainImagePair(_imgPair), this);
  }

  public void dragEnter(DragSourceDragEvent dsde) {}

  public void dragExit(DragSourceEvent dse) {}

  public void dragOver(DragSourceDragEvent dsde) {}

  public void dragDropEnd(DragSourceDropEvent dsde) {
    if (dsde.getDropSuccess())
        ((DefaultListModel<TrainImagePair>)
         _list.getModel()).removeElement(_imgPair);
  }

  public void dropActionChanged(DragSourceDragEvent dsde) {}
}



class TrainImagePairListViewDropHandler extends TransferHandler {
  private TrainImagePairListView _list;

  public TrainImagePairListViewDropHandler(TrainImagePairListView list) {
    this._list = list;
  }

  public boolean canImport(TransferHandler.TransferSupport support) {
    if (!support.isDataFlavorSupported(
            TransferableTrainImagePair.TRAIN_IMAGE_PAIR_FLAVOR)) return false;
    JList.DropLocation dl = (JList.DropLocation) support.getDropLocation();
    return dl.getIndex() != -1;
  }

  public boolean importData(TransferHandler.TransferSupport support) {
    if (!canImport(support)) return false;

    Transferable transferable = support.getTransferable();
    TrainImagePair imgPair;
    try {
      imgPair = (TrainImagePair) transferable.getTransferData(
          TransferableTrainImagePair.TRAIN_IMAGE_PAIR_FLAVOR);
    }
    catch (Exception e) {
      return false;
    }

    JList.DropLocation dl = (JList.DropLocation) support.getDropLocation();
    int dropTargetIndex = dl.getIndex();

    ((DefaultListModel<TrainImagePair>)_list.getModel()).addElement(imgPair);
    return true;
  }
}
