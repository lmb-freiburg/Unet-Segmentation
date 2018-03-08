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

import ij.ImagePlus;

import java.awt.Component;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragGestureRecognizer;
import java.awt.dnd.DragSource;
import java.awt.dnd.DragSourceDragEvent;
import java.awt.dnd.DragSourceDropEvent;
import java.awt.dnd.DragSourceEvent;
import java.awt.dnd.DragSourceListener;
import java.awt.event.KeyListener;
import java.awt.event.KeyEvent;

import java.io.IOException;

import java.util.List;

import javax.swing.ListSelectionModel;
import javax.swing.DefaultListModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DropMode;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.TransferHandler;



public class ImagePlusListView extends JList<ImagePlus> {

  private DefaultListModel<ImagePlus> model;

  public ImagePlusListView() {
    super(new DefaultListModel<ImagePlus>());
    model = (DefaultListModel<ImagePlus>) getModel();
    setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    setDragEnabled(true);
    setDropMode(DropMode.INSERT);
    setTransferHandler(new ImagePlusListViewDropHandler(this));
    setCellRenderer(new ImagePlusListViewNameRenderer());
    new ImagePlusListViewDragListener(this);
    addKeyListener(new ImagePlusListViewKeyListener(this));
  }

}



class ImagePlusListViewNameRenderer extends DefaultListCellRenderer {

  public ImagePlusListViewNameRenderer() {
    setOpaque(true);
  }

  @Override
  public Component getListCellRendererComponent(
      JList<?> list, Object img, int index,
      boolean isSelected, boolean cellHasFocus) {
    setText(((ImagePlus)img).getTitle());
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



class ImagePlusListViewKeyListener implements KeyListener
{

  private ImagePlusListView _list;

  public ImagePlusListViewKeyListener(ImagePlusListView list) {
    this._list = list;
  }

  public void keyPressed(KeyEvent e) {
    if (e.getKeyCode() == KeyEvent.VK_DELETE)
        for (ImagePlus img : (List<ImagePlus>)_list.getSelectedValuesList())
            ((DefaultListModel)_list.getModel()).removeElement(img);
  }

  public void keyReleased(KeyEvent e) {}

  public void keyTyped(KeyEvent e) {}

}



class TransferableImagePlus implements Transferable {

  public static final DataFlavor IMAGE_PLUS_FLAVOR =
      new DataFlavor(ImagePlus.class, "java/ImagePlus");

  private ImagePlus _img;

  public TransferableImagePlus(ImagePlus img) {
    this._img = img;
  }

  @Override
  public DataFlavor[] getTransferDataFlavors() {
    return new DataFlavor[]{IMAGE_PLUS_FLAVOR};
  }

  @Override
  public boolean isDataFlavorSupported(DataFlavor flavor) {
    return flavor.equals(IMAGE_PLUS_FLAVOR);
  }

  @Override
  public Object getTransferData(DataFlavor flavor)
      throws UnsupportedFlavorException, IOException {
    return _img;
  }
}



class ImagePlusListViewDragListener
    implements DragSourceListener, DragGestureListener {
  private ImagePlusListView _list;
  private ImagePlus _img = null;
  private DragSource _ds = new DragSource();

  public ImagePlusListViewDragListener(ImagePlusListView list) {
    this._list = list;
    DragGestureRecognizer dgr =
        _ds.createDefaultDragGestureRecognizer(
            _list, DnDConstants.ACTION_MOVE, this);
  }

  public void dragGestureRecognized(DragGestureEvent dge) {
    _img = _list.getSelectedValue();
    _ds.startDrag(dge, DragSource.DefaultMoveDrop,
                 new TransferableImagePlus(_img), this);
  }

  public void dragEnter(DragSourceDragEvent dsde) {}

  public void dragExit(DragSourceEvent dse) {}

  public void dragOver(DragSourceDragEvent dsde) {}

  public void dragDropEnd(DragSourceDropEvent dsde) {
    if (dsde.getDropSuccess())
        ((DefaultListModel<ImagePlus>)_list.getModel()).removeElement(_img);
  }

  public void dropActionChanged(DragSourceDragEvent dsde) {}
}



class ImagePlusListViewDropHandler extends TransferHandler {
  private ImagePlusListView _list;

  public ImagePlusListViewDropHandler(ImagePlusListView list) {
    this._list = list;
  }

  public boolean canImport(TransferHandler.TransferSupport support) {
    if (!support.isDataFlavorSupported(
            TransferableImagePlus.IMAGE_PLUS_FLAVOR)) return false;
    JList.DropLocation dl = (JList.DropLocation) support.getDropLocation();
    return dl.getIndex() != -1;
  }

  public boolean importData(TransferHandler.TransferSupport support) {
    if (!canImport(support)) return false;

    Transferable transferable = support.getTransferable();
    ImagePlus img;
    try {
      img = (ImagePlus) transferable.getTransferData(
          TransferableImagePlus.IMAGE_PLUS_FLAVOR);
    }
    catch (Exception e) {
      return false;
    }

    JList.DropLocation dl = (JList.DropLocation) support.getDropLocation();
    int dropTargetIndex = dl.getIndex();

    ((DefaultListModel<ImagePlus>)_list.getModel()).addElement(img);
    return true;
  }
}
