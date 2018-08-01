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

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;

import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.JComboBox;
import javax.swing.JButton;
import javax.swing.BorderFactory;
import javax.swing.GroupLayout;

import java.util.Vector;

public class TrainImagePair
{

  // Input images
  private final ImagePlus rawdata;
  private final ImagePlus rawlabels;

  public TrainImagePair(ImagePlus rawdata, ImagePlus rawlabels)
      throws TrainImagePairException {
    if (rawdata == null)
        throw new TrainImagePairException(
            "You must provide an ImagePlus containing raw intensities.");
    if (rawlabels == null)
        throw new TrainImagePairException(
            "You must provide an ImagePlus containing instance labels.");
    if (rawlabels.getBitDepth() > 16)
        throw new TrainImagePairException(
            "The label image must be either 8Bit or 16Bit integer");
    if (rawdata.getNFrames() != rawlabels.getNFrames() ||
        rawdata.getNSlices() != rawlabels.getNSlices() ||
        rawdata.getWidth() != rawlabels.getWidth() ||
        rawdata.getHeight() != rawlabels.getHeight())
        throw new TrainImagePairException(
            "The provided label hyperstack " + rawlabels.getTitle() +
            " is incompatible to the raw data hyperstack " +
            rawdata.getTitle());

    this.rawdata = rawdata;
    this.rawlabels = rawlabels;
  }

  public ImagePlus rawdata() {
    return rawdata;
  }

  public ImagePlus rawlabels() {
    return rawlabels;
  }

  public static TrainImagePair selectImagePair() {
    int[] ids = WindowManager.getIDList();

    int nPotentialLabelImages = 0;
    for (int id : ids) {
      ImagePlus imp = WindowManager.getImage(id);
      if (imp.getBitDepth() <= 16 && imp.getNChannels() == 1)
          nPotentialLabelImages++;
    }
    int[] labelIds = new int[nPotentialLabelImages];
    int i = 0;
    for (int id : ids) {
      ImagePlus imp = WindowManager.getImage(id);
      if (imp.getBitDepth() <= 16 && imp.getNChannels() == 1)
          labelIds[i++] = id;
    }
    Vector<ImagePlus> images = new Vector<ImagePlus>();
    Vector<String> names = new Vector<String>();
    Vector<ImagePlus> labelImages = new Vector<ImagePlus>();
    Vector<String> labelNames = new Vector<String>();
    for (int id : ids) {
      images.add(WindowManager.getImage(id));
      names.add(WindowManager.getImage(id).getTitle());
      ImagePlus imp = WindowManager.getImage(id);
      if (imp.getBitDepth() <= 16 && imp.getNChannels() == 1) {
        labelImages.add(WindowManager.getImage(id));
        labelNames.add(WindowManager.getImage(id).getTitle());
      }
    }

    if (images.size() < 1 || labelImages.size() < 1) {
      IJ.error("No label images found. Label images must be single " +
               "channel 8- or 16-bit integer.");
      return null;
    }

    final JLabel rawLabel = new JLabel("Raw Image");
    final JComboBox<String> rawBox = new JComboBox<String>(names);
    final JLabel labelLabel = new JLabel("Labels");
    final JComboBox<String> labelBox = new JComboBox<String>(labelNames);

    final JPanel dlgPanel = new JPanel();
    dlgPanel.setBorder(BorderFactory.createEtchedBorder());
    final GroupLayout dlgLayout = new GroupLayout(dlgPanel);
    dlgPanel.setLayout(dlgLayout);
    dlgLayout.setAutoCreateGaps(true);
    dlgLayout.setAutoCreateContainerGaps(true);
    dlgLayout.setHorizontalGroup(
        dlgLayout.createSequentialGroup()
        .addGroup(
            dlgLayout.createParallelGroup(GroupLayout.Alignment.TRAILING)
            .addComponent(rawLabel).addComponent(labelLabel))
        .addGroup(
            dlgLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addComponent(rawBox).addComponent(labelBox)));
    dlgLayout.setVerticalGroup(
        dlgLayout.createSequentialGroup()
        .addGroup(
            dlgLayout.createParallelGroup(GroupLayout.Alignment.BASELINE)
            .addComponent(rawLabel).addComponent(rawBox))
        .addGroup(
            dlgLayout.createParallelGroup(GroupLayout.Alignment.BASELINE)
            .addComponent(labelLabel).addComponent(labelBox)));

    final JPanel okCancelPanel = new JPanel();
    JButton okButton = new JButton("OK");
    JButton cancelButton = new JButton("Cancel");
    okCancelPanel.add(okButton);
    okCancelPanel.add(cancelButton);

    JDialog dlg = new JDialog(
        WindowManager.getActiveWindow(), "Image Pair selection",
        Dialog.ModalityType.APPLICATION_MODAL);
    dlg.add(dlgPanel, BorderLayout.CENTER);
    dlg.add(okCancelPanel, BorderLayout.SOUTH);
    dlg.getRootPane().setDefaultButton(okButton);
    dlg.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
    dlg.pack();
    dlg.setMinimumSize(dlg.getPreferredSize());
    dlg.setMaximumSize(new Dimension(Integer.MAX_VALUE,
                                     dlg.getPreferredSize().height));
    dlg.setLocationRelativeTo(WindowManager.getActiveWindow());

    okButton.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            dlg.setVisible(false);
          }});

    cancelButton.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            dlg.dispose();
          }});

    dlg.setVisible(true);

    if (!dlg.isDisplayable()) return null;

    try {
      return new TrainImagePair(
          images.get(rawBox.getSelectedIndex()),
          labelImages.get(labelBox.getSelectedIndex()));
    }
    catch (TrainImagePairException e) {
      IJ.error(e.toString());
      return null;
    }
  }

  public String toString() {
    return rawdata.getShortTitle() + " <-> " + rawlabels.getShortTitle();
  }

}
