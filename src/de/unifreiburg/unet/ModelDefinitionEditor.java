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
import ij.plugin.PlugIn;
import ij.WindowManager;

import java.awt.BorderLayout;

import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Group;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JLabel;
import javax.swing.SpinnerNumberModel;
import javax.swing.JTextField;
import javax.swing.BorderFactory;

import java.io.File;

import caffe.Caffe;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.TextFormat.Parser;
import com.google.protobuf.TextFormat.ParseException;
import com.google.protobuf.TextFormat;

public class ModelDefinitionEditor implements PlugIn {

  private ModelDefinition _model = new ModelDefinition();
  private Caffe.NetParameter.Builder _netBuilder =
      Caffe.NetParameter.newBuilder();
  private JDialog _parametersDialog = new JDialog(
    WindowManager.getCurrentWindow(), "U-Net Model Editor", true);
  private JPanel _dialogPanel = new JPanel();
  private GroupLayout _dialogLayout = new GroupLayout(_dialogPanel);
  private Group _horizontalDialogLayoutGroup =
    _dialogLayout.createParallelGroup(GroupLayout.Alignment.LEADING);
  private Group _verticalDialogLayoutGroup =
    _dialogLayout.createSequentialGroup();
  
  private JTextField _id = new JTextField("U-Net");
  private JTextField _filename = new JTextField("modeldef.h5");
  private JSpinner _dimension = new JSpinner();
  private JSpinner[] _levels = new JSpinner[] {
    new JSpinner(), new JSpinner(), new JSpinner() };
  private JSpinner _nChannels = new JSpinner();
  private JSpinner[] _elementSizeUm = new JSpinner[] {
    new JSpinner(new SpinnerNumberModel(1.0, 0.0000001, 1000000.0, 0.01)),
    new JSpinner(new SpinnerNumberModel(1.0, 0.0000001, 1000000.0, 0.01)),
    new JSpinner(new SpinnerNumberModel(1.0, 0.0000001, 1000000.0, 0.01)) };

  public ModelDefinitionEditor() {
    
    _model.file = new File(_filename.getText());
    _model.id = _id.getText();
    _model.name = _id.getText();
    _model.description = _id.getText();
    _model.inputBlobName = "data3";
    _model.padding = "mirror";
    _model.classNames = new String[] { "foreground", "background" };
    _model.setElementSizeUm(new double[] { 1.0, 1.0 });

    JLabel idLabel = new JLabel("Network ID:");
    JLabel fileLabel = new JLabel("Filename:");
    JLabel dimensionLabel = new JLabel("Dimensionality:");
    ((SpinnerNumberModel)_dimension.getModel()).setMinimum(new Integer(2));
    ((SpinnerNumberModel)_dimension.getModel()).setMaximum(new Integer(3));
    ((SpinnerNumberModel)_dimension.getModel()).setValue(new Integer(2));
    JLabel levelsLabel = new JLabel("Resolution levels:");
    for (int d = 0; d < 3; ++d) {
      ((SpinnerNumberModel)_levels[d].getModel()).setMinimum(new Integer(0));
      ((SpinnerNumberModel)_levels[d].getModel()).setMaximum(
        Integer.MAX_VALUE);
      ((SpinnerNumberModel)_levels[d].getModel()).setValue(new Integer(4));
    }
    JLabel nChannelsLabel = new JLabel("Base channels:");
    ((SpinnerNumberModel)_nChannels.getModel()).setMinimum(new Integer(1));
    ((SpinnerNumberModel)_nChannels.getModel()).setMaximum(Integer.MAX_VALUE);
    ((SpinnerNumberModel)_nChannels.getModel()).setValue(new Integer(64));
    JLabel elSizeLabel = new JLabel("Element Size [µm]:");
    for (int d = 0; d < 3; ++d) {
      ((SpinnerNumberModel)_elementSizeUm[d].getModel()).setMinimum(
        new Double(0.0));
      ((SpinnerNumberModel)_elementSizeUm[d].getModel()).setMaximum(
        Double.POSITIVE_INFINITY);
      ((SpinnerNumberModel)_elementSizeUm[d].getModel()).setValue(
        new Double(1.0));
      ((SpinnerNumberModel)_elementSizeUm[d].getModel()).setStepSize(
        new Double(0.001));
    }
    
    _dialogLayout.setHorizontalGroup(
      _dialogLayout.createSequentialGroup()
      .addGroup(
        _dialogLayout.createParallelGroup(GroupLayout.Alignment.TRAILING)
        .addComponent(idLabel)
        .addComponent(fileLabel)
        .addComponent(dimensionLabel)
        .addComponent(levelsLabel)
        .addComponent(nChannelsLabel)
        .addComponent(elSizeLabel))
      .addGroup(
        _dialogLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
        .addComponent(_id)
        .addComponent(_filename)
        .addComponent(_dimension)
        .addGroup(
          _dialogLayout.createSequentialGroup()
          .addComponent(_levels[0])
          .addComponent(_levels[1])
          .addComponent(_levels[2])
        )
        .addComponent(_nChannels)
        .addGroup(
          _dialogLayout.createSequentialGroup()
          .addComponent(_elementSizeUm[0])
          .addComponent(_elementSizeUm[1])
          .addComponent(_elementSizeUm[2])
        )
      )
    );
    
    _dialogLayout.setVerticalGroup(
      _dialogLayout.createSequentialGroup()
      .addGroup(
        _dialogLayout.createParallelGroup(GroupLayout.Alignment.BASELINE)
        .addComponent(idLabel).addComponent(_id)
      )
      .addGroup(
        _dialogLayout.createParallelGroup(GroupLayout.Alignment.BASELINE)
        .addComponent(fileLabel).addComponent(_filename)
      )
      .addGroup(
        _dialogLayout.createParallelGroup(GroupLayout.Alignment.BASELINE)
        .addComponent(dimensionLabel).addComponent(_dimension)
      )
      .addGroup(
        _dialogLayout.createParallelGroup(GroupLayout.Alignment.BASELINE)
        .addComponent(levelsLabel)
        .addComponent(_levels[0])
        .addComponent(_levels[1])
        .addComponent(_levels[2])
      )
      .addGroup(
        _dialogLayout.createParallelGroup(GroupLayout.Alignment.BASELINE)
        .addComponent(nChannelsLabel).addComponent(_nChannels)
      )
      .addGroup(
        _dialogLayout.createParallelGroup(GroupLayout.Alignment.BASELINE)
        .addComponent(elSizeLabel)
        .addComponent(_elementSizeUm[0])
        .addComponent(_elementSizeUm[1])
        .addComponent(_elementSizeUm[2])
      )
    );
    
    _dialogPanel.setBorder(BorderFactory.createEtchedBorder());
    _dialogPanel.setLayout(_dialogLayout);
    _dialogLayout.setAutoCreateGaps(true);
    _dialogLayout.setAutoCreateContainerGaps(true);
    
    _parametersDialog.add(_dialogPanel, BorderLayout.CENTER);

    update();
  }

  public void fromModeldef(File modeldefFile) {
    _model.load(modeldefFile);
    _netBuilder = Caffe.NetParameter.newBuilder();
    try {
      TextFormat.getParser().merge(_model.modelPrototxt, _netBuilder);
      update();
    }
    catch (ParseException e) {
      IJ.log("Could not parse model prototxt from " +
        modeldefFile.getAbsolutePath());
    }
  }

  public void update() {}

  @Override
  public void run(String arg) {
    _parametersDialog.show();
  }

};
