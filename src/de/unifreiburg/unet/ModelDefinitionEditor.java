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
import ij.Prefs;

import java.awt.Dimension;
import java.awt.Insets;
import java.awt.BorderLayout;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

import javax.swing.event.ChangeListener;
import javax.swing.event.ChangeEvent;
import javax.swing.BoxLayout;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Group;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JLabel;
import javax.swing.SpinnerNumberModel;
import javax.swing.JTextField;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.UIManager;
import javax.swing.Icon;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;

import java.io.File;
import java.io.IOException;

import ncsa.hdf.hdf5lib.exceptions.HDF5Exception;

public class ModelDefinitionEditor implements PlugIn {

  private JDialog _parametersDialog = new JDialog(
      WindowManager.getCurrentWindow(), "U-Net Model Editor", true);

  private final JTextField _id = new JTextField(
      Prefs.get("unet.newModel.id", "U-Net-2D-4-64"));
  private final JTextField _name = new JTextField(
      Prefs.get("unet.newModel.name", "U-Net (2D-4-64)"));
  private final JTextField _description = new JTextField(
      Prefs.get("unet.newModel.description",
                "2D U-Net with 4 resolution levels and 64 base channels"));
  private final JTextField _filename = new JTextField(
      Prefs.get("unet.newModel.filename", "u-net-4-64.modeldef.h5"));
  private final JSpinner _dimension =
      new JSpinner(
          new SpinnerNumberModel(
              (int)Prefs.get("unet.newModel.dimension", 2), 2, 3, 1));
  private final JSpinner[] _levels = new JSpinner[] {
      new JSpinner(
          new SpinnerNumberModel(
              (int)Prefs.get("unet.newModel.levelsX", 4), 0,
              Integer.MAX_VALUE, 1)),
      new JSpinner(
          new SpinnerNumberModel(
              (int)Prefs.get("unet.newModel.levelsY", 4), 0,
              Integer.MAX_VALUE, 1)),
      new JSpinner(
          new SpinnerNumberModel(
              (int)Prefs.get("unet.newModel.levelsZ", 3), 0,
              Integer.MAX_VALUE, 1))
  };
  private final JSpinner _nChannels =
      new JSpinner(
          new SpinnerNumberModel(
              (int)Prefs.get("unet.newModel.nChannels", 64), 1,
              Integer.MAX_VALUE, 1));
  private final JSpinner[] _elementSizeUm = new JSpinner[] {
      new JSpinner(
          new SpinnerNumberModel(
              (double)Prefs.get("unet.newModel.elSizeX", 0.5),
              0.0000001, 1000000.0, 0.01)),
      new JSpinner(
          new SpinnerNumberModel(
              (double)Prefs.get("unet.newModel.elSizeY", 0.5),
              0.0000001, 1000000.0, 0.01)),
      new JSpinner(
          new SpinnerNumberModel(
              (double)Prefs.get("unet.newModel.elSizeZ", 1.0),
              0.0000001, 1000000.0, 0.01))
  };
  private final JSpinner _diskRadiusPx = new JSpinner(
      new SpinnerNumberModel(
          (int)Prefs.get("unet.newModel.diskRadiusPx", 2), 0, 100, 1));
  private final JSpinner _borderWeightFactor = new JSpinner(
      new SpinnerNumberModel(
          (double)Prefs.get("unet.newModel.borderWeightFactor", 50.0),
          0.0, 1000000.0, 0.1));
  private final JSpinner _borderWeightSigmaPx = new JSpinner(
      new SpinnerNumberModel(
          (double)Prefs.get("unet.newModel.borderWeightSigmaPx", 6.0),
          0.0, 1000.0, 0.1));
  private final JSpinner _foregroundBackgroundRatio = new JSpinner(
      new SpinnerNumberModel(
          (double)Prefs.get("unet.newModel.foregroundBackgroundRatio", 0.1),
          0.0, 1000000.0, 0.01));
  private final JSpinner _borderSmoothnessSigmaPx = new JSpinner(
      new SpinnerNumberModel(
          (double)Prefs.get("unet.newModel.borderSmoothnessSigmaPx", 10.0),
          0.0, 1000.0, 0.1));
  private final JSpinner[] _minAngles = new JSpinner[] {
      new JSpinner(
          new SpinnerNumberModel(
              (double)Prefs.get("unet.newModel.minAnglesPhi", 0.0), -360.0,
              (double)Prefs.get("unet.newModel.maxAnglesPhi", 360.0), 1.0)),
      new JSpinner(
          new SpinnerNumberModel(
              (double)Prefs.get("unet.newModel.minAnglesTheta", 0.0), -360.0,
              (double)Prefs.get("unet.newModel.maxAnglesTheta", 0.0), 1.0)),
      new JSpinner(
          new SpinnerNumberModel(
              (double)Prefs.get("unet.newModel.minAnglesPsi", 0.0), -360.0,
              (double)Prefs.get("unet.newModel.maxAnglesPsi", 0.0), 1.0)) };
  private final JSpinner[] _maxAngles = new JSpinner[] {
      new JSpinner(
          new SpinnerNumberModel(
              (double)Prefs.get("unet.newModel.maxAnglesPhi", 360.0),
              (double)Prefs.get("unet.newModel.minAnglesPhi", 0.0),
              360.0, 1.0)),
      new JSpinner(
          new SpinnerNumberModel(
              (double)Prefs.get("unet.newModel.maxAnglesTheta", 0.0),
              (double)Prefs.get("unet.newModel.minAnglesTheta", 0.0),
              360.0, 1.0)),
      new JSpinner(
          new SpinnerNumberModel(
              (double)Prefs.get("unet.newModel.maxAnglesPsi", 0.0),
              (double)Prefs.get("unet.newModel.minAnglesPsi", 0.0),
              360.0, 1.0)) };
  private final JCheckBox _mirroring = new JCheckBox();
  private final JSpinner[] _deformationGrid = new JSpinner[] {
      new JSpinner(
          new SpinnerNumberModel(
              (int)Prefs.get("unet.newModel.deformationGridX", 150),
              1, Integer.MAX_VALUE, 1)),
      new JSpinner(
          new SpinnerNumberModel(
              (int)Prefs.get("unet.newModel.deformationGridY", 150),
              1, Integer.MAX_VALUE, 1)),
      new JSpinner(
          new SpinnerNumberModel(
              (int)Prefs.get("unet.newModel.deformationGridZ", 150),
              1, Integer.MAX_VALUE, 1))
  };
  private final JSpinner[] _deformationMagnitude = new JSpinner[] {
      new JSpinner(
          new SpinnerNumberModel(
              (int)Prefs.get("unet.newModel.deformationMagnitudeX", 10),
              0, Integer.MAX_VALUE, 1)),
      new JSpinner(
          new SpinnerNumberModel(
              (int)Prefs.get("unet.newModel.deformationMagnitudeY", 10),
              0, Integer.MAX_VALUE, 1)),
      new JSpinner(
          new SpinnerNumberModel(
              (int)Prefs.get("unet.newModel.deformationMagnitudeZ", 10),
              0, Integer.MAX_VALUE, 1)),
  };
  private final JSpinner[] _minValueRange = new JSpinner[] {
      new JSpinner(
          new SpinnerNumberModel(
              (double)Prefs.get("unet.newModel.minValueRangeStart", -0.05),
              -1.0, (double)Prefs.get("unet.newModel.minValueRangeEnd", 0.05),
              0.01)),
      new JSpinner(
          new SpinnerNumberModel(
              (double)Prefs.get("unet.newModel.minValueRangeEnd", 0.05),
              (double)Prefs.get("unet.newModel.minValueRangeStart", -0.05),
              (double)Prefs.get("unet.newModel.maxValueRangeStart", 0.95),
              0.01))
  };
  private final JSpinner[] _maxValueRange = new JSpinner[] {
      new JSpinner(
          new SpinnerNumberModel(
              (double)Prefs.get("unet.newModel.maxValueRangeStart", 0.95),
              (double)Prefs.get("unet.newModel.minValueRangeEnd", 0.05),
              (double)Prefs.get("unet.newModel.maxValueRangeEnd", 1.05), 0.01)),
      new JSpinner(
          new SpinnerNumberModel(
              (double)Prefs.get("unet.newModel.maxValueRangeEnd", 1.05),
              (double)Prefs.get("unet.newModel.maxValueRangeStart", 0.95),
              2.0, 0.01))
  };
  private final JSpinner[] _slopeRange = new JSpinner[] {
      new JSpinner(
          new SpinnerNumberModel(
              (double)Prefs.get("unet.newModel.slopeStart", 0.8), 0.1,
              (double)Prefs.get("unet.newModel.slopeEnd", 1.2), 0.01)),
      new JSpinner(
          new SpinnerNumberModel(
              (double)Prefs.get("unet.newModel.slopeEnd", 1.2),
              (double)Prefs.get("unet.newModel.slopeStart", 0.8), 10.0, 0.01))
  };

  public ModelDefinitionEditor() {

    // General information
    JLabel fileLabel = new JLabel("Filename:");
    _filename.setToolTipText(
        "Output file to save the model to. We " +
        "recommend to use the composite extension .modeldef.h5");
    JButton fileChooseButton =
        (UIManager.get("FileView.directoryIcon") instanceof Icon) ? new JButton(
            (Icon)UIManager.get("FileView.directoryIcon")) : new JButton("...");
    int marginTop = (int) Math.ceil(
        (fileChooseButton.getPreferredSize().getHeight() -
         _filename.getPreferredSize().getHeight()) / 2.0);
    int marginBottom = (int) Math.floor(
        (fileChooseButton.getPreferredSize().getHeight() -
         _filename.getPreferredSize().getHeight()) / 2.0);
    Insets insets = fileChooseButton.getMargin();
    insets.top -= marginTop;
    insets.left = 1;
    insets.bottom -= marginBottom;
    insets.right = 1;
    fileChooseButton.setMargin(insets);
    fileChooseButton.setToolTipText("Select file from filesystem");
    JLabel idLabel = new JLabel("Network ID:");
    _id.setToolTipText("This ID is used as prefix for model-related " +
                       "files, avoid whitespace and special characters.");
    JLabel nameLabel = new JLabel("Network Name:");
    _name.setToolTipText("This name is shown in the Model selection combo " +
                         "boxes.");
    JLabel descriptionLabel = new JLabel("Description:");
    _description.setToolTipText(
        "This description is not actually used, but is stored to the model " +
        "for more detailed information on the experiment.");
    JPanel generalInfoPanel = new JPanel();
    GroupLayout generalInfoPanelLayout = new GroupLayout(generalInfoPanel);
    generalInfoPanel.setLayout(generalInfoPanelLayout);
    generalInfoPanelLayout.setAutoCreateGaps(true);
    generalInfoPanelLayout.setAutoCreateContainerGaps(true);
    generalInfoPanel.setBorder(
        BorderFactory.createTitledBorder("General"));
    generalInfoPanelLayout.setHorizontalGroup(
        generalInfoPanelLayout.createSequentialGroup()
        .addGroup(
            generalInfoPanelLayout.createParallelGroup(
                GroupLayout.Alignment.TRAILING)
            .addComponent(fileLabel).addComponent(idLabel)
            .addComponent(nameLabel).addComponent(descriptionLabel))
        .addGroup(
            generalInfoPanelLayout.createParallelGroup(
                GroupLayout.Alignment.LEADING)
            .addGroup(
                generalInfoPanelLayout.createSequentialGroup()
                .addComponent(_filename).addComponent(fileChooseButton))
            .addComponent(_id).addComponent(_name)
            .addComponent(_description)));
    generalInfoPanelLayout.setVerticalGroup(
        generalInfoPanelLayout.createSequentialGroup()
        .addGroup(
            generalInfoPanelLayout.createParallelGroup(
                GroupLayout.Alignment.BASELINE)
            .addComponent(fileLabel).addComponent(_filename)
            .addComponent(fileChooseButton))
        .addGroup(
            generalInfoPanelLayout.createParallelGroup(
                GroupLayout.Alignment.BASELINE)
            .addComponent(idLabel).addComponent(_id))
        .addGroup(
            generalInfoPanelLayout.createParallelGroup(
                GroupLayout.Alignment.BASELINE)
            .addComponent(nameLabel).addComponent(_name))
        .addGroup(
            generalInfoPanelLayout.createParallelGroup(
                GroupLayout.Alignment.BASELINE)
            .addComponent(descriptionLabel).addComponent(_description)));

    // Architecture (goes to prototxt)
    JLabel dimensionLabel = new JLabel("Dimensionality:");
    _dimension.setToolTipText("2: 2D image data, 3: 3D volume data (stacks)");
    JLabel levelsLabel = new JLabel("Resolution levels:");
    _levels[0].setToolTipText("Number of sub-sampling steps in x-direction");
    _levels[1].setToolTipText("Number of sub-sampling steps in y-direction");
    _levels[2].setToolTipText("Number of sub-sampling steps in z-direction");
    JLabel[] levelDimLabels = new JLabel[] {
        new JLabel(" x:"), new JLabel(" y:"), new JLabel(" z:") };
    JLabel nChannelsLabel = new JLabel("Base channels:");
    _nChannels.setToolTipText(
        "The number of channels at original " +
        "resolution. With each sub-sampling step the number of channels " +
        "is doubled.");
    JPanel architecturePanel = new JPanel();
    GroupLayout architecturePanelLayout = new GroupLayout(architecturePanel);
    architecturePanel.setLayout(architecturePanelLayout);
    architecturePanelLayout.setAutoCreateGaps(true);
    architecturePanelLayout.setAutoCreateContainerGaps(true);
    architecturePanel.setBorder(
        BorderFactory.createTitledBorder("Architecture"));
    architecturePanelLayout.setHorizontalGroup(
        architecturePanelLayout.createSequentialGroup()
        .addGroup(
            architecturePanelLayout.createParallelGroup(
                GroupLayout.Alignment.TRAILING)
            .addComponent(dimensionLabel).addComponent(levelsLabel)
            .addComponent(nChannelsLabel))
        .addGroup(
            architecturePanelLayout.createParallelGroup(
                GroupLayout.Alignment.LEADING)
            .addComponent(_dimension)
            .addGroup(
                architecturePanelLayout.createSequentialGroup()
                .addComponent(levelDimLabels[0]).addComponent(_levels[0])
                .addComponent(levelDimLabels[1]).addComponent(_levels[1])
                .addComponent(levelDimLabels[2]).addComponent(_levels[2]))
            .addComponent(_nChannels)));
    architecturePanelLayout.setVerticalGroup(
        architecturePanelLayout.createSequentialGroup()
        .addGroup(
            architecturePanelLayout.createParallelGroup(
                GroupLayout.Alignment.BASELINE)
            .addComponent(dimensionLabel).addComponent(_dimension))
        .addGroup(
            architecturePanelLayout.createParallelGroup(
                GroupLayout.Alignment.BASELINE)
            .addComponent(levelsLabel)
            .addComponent(levelDimLabels[0]).addComponent(_levels[0])
            .addComponent(levelDimLabels[1]).addComponent(_levels[1])
            .addComponent(levelDimLabels[2]).addComponent(_levels[2]))
        .addGroup(
            architecturePanelLayout.createParallelGroup(
                GroupLayout.Alignment.BASELINE)
            .addComponent(nChannelsLabel).addComponent(_nChannels)));

    // Data pre-processing (goes to attributes)
    JLabel elSizeLabel = new JLabel("Element Size [\u00b5m]:");
    JLabel[] elSizeDimLabels = new JLabel[] {
        new JLabel(" x:"), new JLabel(" y:"), new JLabel(" z:") };
    for (int d = 0; d < 3; ++d)
        ((JSpinner.NumberEditor)_elementSizeUm[d].getEditor())
            .getFormat().applyPattern("######0.0######");
    _elementSizeUm[0].setToolTipText(
        "Resize input images to given pixel width");
    _elementSizeUm[1].setToolTipText(
        "Resize input images to given pixel height");
    _elementSizeUm[2].setToolTipText(
        "Resize input images to given voxel depth");
    JLabel diskRadiusPxLabel = new JLabel("Detection disk radius [px]:");
    _diskRadiusPx.setToolTipText("Radius of detection disks in pixels.");
    JLabel borderWeightFactorLabel = new JLabel("Ridge weight:");
    ((JSpinner.NumberEditor)_borderWeightFactor.getEditor())
        .getFormat().applyPattern("######0.0######");
    _borderWeightFactor.setToolTipText(
        "Loss weight of artificially introduces instance separation ridges.");
    JLabel borderWeightSigmaPxLabel = new JLabel("Ridge width sigma [px]:");
    ((JSpinner.NumberEditor)_borderWeightSigmaPx.getEditor())
        .getFormat().applyPattern("######0.0######");
    _borderWeightSigmaPx.setToolTipText(
        "The half-width of the Gaussian ridge weight function.");
    JLabel foregroundBackgroundRatioLabel =
        new JLabel("Foreground/Background ratio:");
    ((JSpinner.NumberEditor)_foregroundBackgroundRatio.getEditor())
        .getFormat().applyPattern("######0.0######");
    _foregroundBackgroundRatio.setToolTipText(
        "Loss weight for background pixels. Foreground pixel weight is 1");
    JLabel borderSmoothnessSigmaPxLabel =
        new JLabel("Boundary smoothness sigma [px]:");
    ((JSpinner.NumberEditor)_borderSmoothnessSigmaPx.getEditor())
        .getFormat().applyPattern("######0.0######");
    _borderSmoothnessSigmaPx.setToolTipText(
        "The half-width of the Gaussian transition between foreground and " +
        "background");
    JPanel preprocessingPanel = new JPanel();
    GroupLayout preprocessingPanelLayout = new GroupLayout(preprocessingPanel);
    preprocessingPanel.setLayout(preprocessingPanelLayout);
    preprocessingPanelLayout.setAutoCreateGaps(true);
    preprocessingPanelLayout.setAutoCreateContainerGaps(true);
    preprocessingPanel.setBorder(
        BorderFactory.createTitledBorder("Pre-Processing"));
    preprocessingPanelLayout.setHorizontalGroup(
        preprocessingPanelLayout.createSequentialGroup()
        .addGroup(
            preprocessingPanelLayout.createParallelGroup(
                GroupLayout.Alignment.TRAILING)
            .addComponent(elSizeLabel)
            .addComponent(diskRadiusPxLabel)
            .addComponent(borderWeightFactorLabel)
            .addComponent(borderWeightSigmaPxLabel)
            .addComponent(foregroundBackgroundRatioLabel)
            .addComponent(borderSmoothnessSigmaPxLabel))
        .addGroup(
            preprocessingPanelLayout.createParallelGroup(
                GroupLayout.Alignment.LEADING)
            .addGroup(
                preprocessingPanelLayout.createSequentialGroup()
                .addComponent(elSizeDimLabels[0])
                .addComponent(_elementSizeUm[0])
                .addComponent(elSizeDimLabels[1])
                .addComponent(_elementSizeUm[1])
                .addComponent(elSizeDimLabels[2])
                .addComponent(_elementSizeUm[2]))
            .addComponent(_diskRadiusPx)
            .addComponent(_borderWeightFactor)
            .addComponent(_borderWeightSigmaPx)
            .addComponent(_foregroundBackgroundRatio)
            .addComponent(_borderSmoothnessSigmaPx)));
    preprocessingPanelLayout.setVerticalGroup(
        preprocessingPanelLayout.createSequentialGroup()
        .addGroup(
            preprocessingPanelLayout.createParallelGroup(
                GroupLayout.Alignment.BASELINE)
            .addComponent(elSizeLabel)
            .addComponent(elSizeDimLabels[0]).addComponent(_elementSizeUm[0])
            .addComponent(elSizeDimLabels[1]).addComponent(_elementSizeUm[1])
            .addComponent(elSizeDimLabels[2]).addComponent(_elementSizeUm[2]))
        .addGroup(
            preprocessingPanelLayout.createParallelGroup(
                GroupLayout.Alignment.BASELINE)
            .addComponent(diskRadiusPxLabel)
            .addComponent(_diskRadiusPx))
        .addGroup(
            preprocessingPanelLayout.createParallelGroup(
                GroupLayout.Alignment.BASELINE)
            .addComponent(borderWeightFactorLabel)
            .addComponent(_borderWeightFactor))
        .addGroup(
            preprocessingPanelLayout.createParallelGroup(
                GroupLayout.Alignment.BASELINE)
            .addComponent(borderWeightSigmaPxLabel)
            .addComponent(_borderWeightSigmaPx))
        .addGroup(
            preprocessingPanelLayout.createParallelGroup(
                GroupLayout.Alignment.BASELINE)
            .addComponent(foregroundBackgroundRatioLabel)
            .addComponent(_foregroundBackgroundRatio))
        .addGroup(
            preprocessingPanelLayout.createParallelGroup(
                GroupLayout.Alignment.BASELINE)
            .addComponent(borderSmoothnessSigmaPxLabel)
            .addComponent(_borderSmoothnessSigmaPx)));

    // Augmentation (goes to prototxt)
    JLabel rotationLabel = new JLabel("Rotation:");
    for (int d = 0; d < 3; ++d) {
      ((JSpinner.NumberEditor)_minAngles[d].getEditor())
          .getFormat().applyPattern("######0.0######");
      ((JSpinner.NumberEditor)_maxAngles[d].getEditor())
          .getFormat().applyPattern("######0.0######");
    }
    _minAngles[0].setToolTipText(
        "The minimum rotation-angle around the z-axis");
    _maxAngles[0].setToolTipText(
        "The maximum rotation-angle around the z-axis");
    _minAngles[1].setToolTipText(
        "The minimum rotation-angle around the y-axis (after \u03c6 rotation)");
    _minAngles[1].setToolTipText(
        "The maximum rotation-angle around the y-axis (after \u03c6 rotation)");
    _minAngles[2].setToolTipText(
        "The minimum rotation-angle around the z-axis (after \u03c6-\u03b8 " +
        "rotation)");
    _maxAngles[2].setToolTipText(
        "The maximum rotation-angle around the z-axis (after \u03c6-\u03b8 " +
        "rotation)");
    JLabel[] rotationDimLabels = new JLabel[] {
        new JLabel(" \u03c6:"), new JLabel(" \u03b8:"),
        new JLabel(" \u03a8:") };
    JLabel[] rotationDashLabels = new JLabel[] {
        new JLabel("-"), new JLabel("-"), new JLabel("-") };
    JLabel mirroringLabel = new JLabel("Mirroring:");
    _mirroring.setSelected(Prefs.get("unet.newModel.mirroring", false));
    _mirroring.setToolTipText(
        "Check if you want to also train on mirrored tiles");
    JLabel deformationGridLabel = new JLabel("Deformation grid spacing [px]:");
    JLabel[] deformationGridDimLabels = new JLabel[] {
        new JLabel(" x:"), new JLabel(" y:"), new JLabel(" z:") };
    _deformationGrid[0].setToolTipText(
        "Deformation grid point spacing in x direction");
    _deformationGrid[1].setToolTipText(
        "Deformation grid point spacing in y direction");
    _deformationGrid[2].setToolTipText(
        "Deformation grid point spacing in z direction");
    JLabel deformationMagnitudeLabel =
        new JLabel("Deformation magnitude [px]:");
    JLabel[] deformationMagnitudeDimLabels = new JLabel[] {
        new JLabel(" x:"), new JLabel(" y:"), new JLabel(" z:") };
    _deformationMagnitude[0].setToolTipText(
        "Deformation component magnitude in x direction");
    _deformationMagnitude[1].setToolTipText(
        "Deformation component magnitude in y direction");
    _deformationMagnitude[2].setToolTipText(
        "Deformation component magnitude in z direction");
    JLabel valueAugmentationLabel = new JLabel("Intensity Curve:");
    JLabel minValueLabel = new JLabel("Min:");
    JLabel slopeLabel = new JLabel("Slope:");
    JLabel maxValueLabel = new JLabel("Max:");
    for (int d = 0; d < 2; ++d) {
      ((JSpinner.NumberEditor)_minValueRange[d].getEditor())
          .getFormat().applyPattern("######0.0######");
      ((JSpinner.NumberEditor)_slopeRange[d].getEditor())
          .getFormat().applyPattern("######0.0######");
      ((JSpinner.NumberEditor)_maxValueRange[d].getEditor())
          .getFormat().applyPattern("######0.0######");
    }
    _minValueRange[0].setToolTipText(
        "Intensity zero will be mapped to a random number of at least this " +
        "value");
    JLabel minValueDash = new JLabel("-");
    _minValueRange[1].setToolTipText(
        "Intensity zero will be mapped to a random number of at most this " +
        "value");
    _maxValueRange[0].setToolTipText(
        "Intensity one will be mapped to a random number of at least this " +
        "value");
    JLabel maxValueDash = new JLabel("-");
    _maxValueRange[1].setToolTipText(
        "Intensity one will be mapped to a random number of at most this " +
        "value");
    _slopeRange[0].setToolTipText(
        "Gamma curve slope at intensity 0.5 will be at least this value");
    JLabel slopeDash = new JLabel("-");
    _slopeRange[1].setToolTipText(
        "Gamma curve slope at intensity 0.5 will be at most this value");
    JPanel augmentationPanel = new JPanel();
    GroupLayout augmentationPanelLayout = new GroupLayout(augmentationPanel);
    augmentationPanel.setLayout(augmentationPanelLayout);
    augmentationPanelLayout.setAutoCreateGaps(true);
    augmentationPanelLayout.setAutoCreateContainerGaps(true);
    augmentationPanel.setBorder(
        BorderFactory.createTitledBorder("Augmentation"));
    augmentationPanelLayout.setHorizontalGroup(
        augmentationPanelLayout.createSequentialGroup()
        .addGroup(
            augmentationPanelLayout.createParallelGroup(
                GroupLayout.Alignment.TRAILING)
            .addComponent(rotationLabel).addComponent(mirroringLabel)
            .addComponent(deformationGridLabel)
            .addComponent(deformationMagnitudeLabel)
            .addComponent(valueAugmentationLabel)
                  )
        .addGroup(
            augmentationPanelLayout.createParallelGroup(
                GroupLayout.Alignment.LEADING)
            .addGroup(
                augmentationPanelLayout.createSequentialGroup()
                .addComponent(rotationDimLabels[0]).addComponent(_minAngles[0])
                .addComponent(rotationDashLabels[0]).addComponent(_maxAngles[0])
                .addComponent(rotationDimLabels[1]).addComponent(_minAngles[1])
                .addComponent(rotationDashLabels[1]).addComponent(_maxAngles[1])
                .addComponent(rotationDimLabels[2]).addComponent(_minAngles[2])
                .addComponent(rotationDashLabels[2]).addComponent(_maxAngles[2])
                      )
            .addComponent(_mirroring)
            .addGroup(
                augmentationPanelLayout.createSequentialGroup()
                .addComponent(deformationGridDimLabels[0])
                .addComponent(_deformationGrid[0])
                .addComponent(deformationGridDimLabels[1])
                .addComponent(_deformationGrid[1])
                .addComponent(deformationGridDimLabels[2])
                .addComponent(_deformationGrid[2])
                      )
            .addGroup(
                augmentationPanelLayout.createSequentialGroup()
                .addComponent(deformationMagnitudeDimLabels[0])
                .addComponent(_deformationMagnitude[0])
                .addComponent(deformationMagnitudeDimLabels[1])
                .addComponent(_deformationMagnitude[1])
                .addComponent(deformationMagnitudeDimLabels[2])
                .addComponent(_deformationMagnitude[2])
                      )
            .addGroup(
                augmentationPanelLayout.createSequentialGroup()
                .addComponent(minValueLabel).addComponent(_minValueRange[0])
                .addComponent(minValueDash).addComponent(_minValueRange[1])
                .addComponent(slopeLabel).addComponent(_slopeRange[0])
                .addComponent(slopeDash).addComponent(_slopeRange[1])
                .addComponent(maxValueLabel).addComponent(_maxValueRange[0])
                .addComponent(maxValueDash).addComponent(_maxValueRange[1])
                      )
                  )
                                               );
    augmentationPanelLayout.setVerticalGroup(
        augmentationPanelLayout.createSequentialGroup()
        .addGroup(
            augmentationPanelLayout.createParallelGroup(
                GroupLayout.Alignment.BASELINE)
            .addComponent(rotationLabel)
            .addComponent(rotationDimLabels[0]).addComponent(_minAngles[0])
            .addComponent(rotationDashLabels[0]).addComponent(_maxAngles[0])
            .addComponent(rotationDimLabels[1]).addComponent(_minAngles[1])
            .addComponent(rotationDashLabels[1]).addComponent(_maxAngles[1])
            .addComponent(rotationDimLabels[2]).addComponent(_minAngles[2])
            .addComponent(rotationDashLabels[2]).addComponent(_maxAngles[2])
                  )
        .addGroup(
            augmentationPanelLayout.createParallelGroup(
                GroupLayout.Alignment.BASELINE)
            .addComponent(mirroringLabel).addComponent(_mirroring)
                  )
        .addGroup(
            augmentationPanelLayout.createParallelGroup(
                GroupLayout.Alignment.BASELINE)
            .addComponent(deformationGridLabel)
            .addComponent(deformationGridDimLabels[0])
            .addComponent(_deformationGrid[0])
            .addComponent(deformationGridDimLabels[1])
            .addComponent(_deformationGrid[1])
            .addComponent(deformationGridDimLabels[2])
            .addComponent(_deformationGrid[2])
                  )
        .addGroup(
            augmentationPanelLayout.createParallelGroup(
                GroupLayout.Alignment.BASELINE)
            .addComponent(deformationMagnitudeLabel)
            .addComponent(deformationMagnitudeDimLabels[0])
            .addComponent(_deformationMagnitude[0])
            .addComponent(deformationMagnitudeDimLabels[1])
            .addComponent(_deformationMagnitude[1])
            .addComponent(deformationMagnitudeDimLabels[2])
            .addComponent(_deformationMagnitude[2])
                  )
        .addGroup(
            augmentationPanelLayout.createParallelGroup(
                GroupLayout.Alignment.BASELINE)
            .addComponent(valueAugmentationLabel)
            .addComponent(minValueLabel).addComponent(_minValueRange[0])
            .addComponent(minValueDash).addComponent(_minValueRange[1])
            .addComponent(slopeLabel).addComponent(_slopeRange[0])
            .addComponent(slopeDash).addComponent(_slopeRange[1])
            .addComponent(maxValueLabel).addComponent(_maxValueRange[0])
            .addComponent(maxValueDash).addComponent(_maxValueRange[1])
                  )
                                             );

    JPanel dialogPanel = new JPanel();
    dialogPanel.setLayout(new BoxLayout(dialogPanel, BoxLayout.Y_AXIS));
    dialogPanel.setBorder(BorderFactory.createEtchedBorder());
    dialogPanel.add(generalInfoPanel);
    dialogPanel.add(architecturePanel);
    dialogPanel.add(preprocessingPanel);
    dialogPanel.add(augmentationPanel);

    fileChooseButton.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            JFileChooser f = new JFileChooser(new File("."));
            f.setDialogTitle("Select folder and filename");
            f.setMultiSelectionEnabled(false);
            f.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
            f.setFileFilter(
                new FileNameExtensionFilter("HDF5 files", "h5", "H5"));
            int res = f.showDialog(_parametersDialog, "Select");
            if (res != JFileChooser.APPROVE_OPTION) return;
            _filename.setText(f.getSelectedFile().getAbsolutePath());
          }
        }
                                       );

    _dimension.addChangeListener(
        new ChangeListener() {
          @Override
          public void stateChanged(ChangeEvent e) {
            boolean is3D = ((int)_dimension.getModel().getValue() == 3);
            levelDimLabels[2].setVisible(is3D);
            _levels[2].setVisible(is3D);
            elSizeDimLabels[2].setVisible(is3D);
            _elementSizeUm[2].setVisible(is3D);
            for (int d = 1; d < 3; ++d) {
              rotationDimLabels[d].setVisible(is3D);
              _minAngles[d].setVisible(is3D);
              rotationDashLabels[d].setVisible(is3D);
              _maxAngles[d].setVisible(is3D);
            }
            deformationGridDimLabels[2].setVisible(is3D);
            _deformationGrid[2].setVisible(is3D);
            deformationMagnitudeDimLabels[2].setVisible(is3D);
            _deformationMagnitude[2].setVisible(is3D);
          }
        }
                                 );

    _minAngles[0].addChangeListener(
        new ChangeListener() {
          @Override
          public void stateChanged(ChangeEvent e) {
            ((SpinnerNumberModel)_maxAngles[0].getModel()).setMinimum(
                (Double)_minAngles[0].getModel().getValue());
          }
        }
                                    );
    _maxAngles[0].addChangeListener(
        new ChangeListener() {
          @Override
          public void stateChanged(ChangeEvent e) {
            ((SpinnerNumberModel)_minAngles[0].getModel()).setMaximum(
                (Double)_maxAngles[0].getModel().getValue());
          }
        }
                                    );
    _minAngles[1].addChangeListener(
        new ChangeListener() {
          @Override
          public void stateChanged(ChangeEvent e) {
            ((SpinnerNumberModel)_maxAngles[1].getModel()).setMinimum(
                (Double)_minAngles[1].getModel().getValue());
          }
        }
                                    );
    _maxAngles[1].addChangeListener(
        new ChangeListener() {
          @Override
          public void stateChanged(ChangeEvent e) {
            ((SpinnerNumberModel)_minAngles[1].getModel()).setMaximum(
                (Double)_maxAngles[1].getModel().getValue());
          }
        }
                                    );
    _minAngles[2].addChangeListener(
        new ChangeListener() {
          @Override
          public void stateChanged(ChangeEvent e) {
            ((SpinnerNumberModel)_maxAngles[2].getModel()).setMinimum(
                (Double)_minAngles[2].getModel().getValue());
          }
        }
                                    );
    _maxAngles[2].addChangeListener(
        new ChangeListener() {
          @Override
          public void stateChanged(ChangeEvent e) {
            ((SpinnerNumberModel)_minAngles[2].getModel()).setMaximum(
                (Double)_maxAngles[2].getModel().getValue());
          }
        }
                                    );

    _minValueRange[0].addChangeListener(
        new ChangeListener() {
          @Override
          public void stateChanged(ChangeEvent e) {
            ((SpinnerNumberModel)_minValueRange[1].getModel()).setMinimum(
                (Double)_minValueRange[0].getModel().getValue());
          }
        }
                                        );
    _minValueRange[1].addChangeListener(
        new ChangeListener() {
          @Override
          public void stateChanged(ChangeEvent e) {
            ((SpinnerNumberModel)_minValueRange[0].getModel()).setMaximum(
                (Double)_minValueRange[1].getModel().getValue());
            ((SpinnerNumberModel)_maxValueRange[0].getModel()).setMinimum(
                (Double)_minValueRange[1].getModel().getValue());
          }
        }
                                        );
    _slopeRange[0].addChangeListener(
        new ChangeListener() {
          @Override
          public void stateChanged(ChangeEvent e) {
            ((SpinnerNumberModel)_slopeRange[1].getModel()).setMinimum(
                (Double)_slopeRange[0].getModel().getValue());
          }
        }
                                     );
    _slopeRange[1].addChangeListener(
        new ChangeListener() {
          @Override
          public void stateChanged(ChangeEvent e) {
            ((SpinnerNumberModel)_slopeRange[0].getModel()).setMaximum(
                (Double)_slopeRange[1].getModel().getValue());
          }
        }
                                     );
    _maxValueRange[0].addChangeListener(
        new ChangeListener() {
          @Override
          public void stateChanged(ChangeEvent e) {
            ((SpinnerNumberModel)_minValueRange[1].getModel()).setMaximum(
                (Double)_maxValueRange[0].getModel().getValue());
            ((SpinnerNumberModel)_maxValueRange[1].getModel()).setMinimum(
                (Double)_maxValueRange[0].getModel().getValue());
          }
        }
                                        );
    _maxValueRange[1].addChangeListener(
        new ChangeListener() {
          @Override
          public void stateChanged(ChangeEvent e) {
            ((SpinnerNumberModel)_maxValueRange[0].getModel()).setMaximum(
                (Double)_maxValueRange[1].getModel().getValue());
          }
        }
                                        );

    _parametersDialog.add(dialogPanel, BorderLayout.CENTER);

    // OK/Cancel buttons
    JPanel okCancelPanel = new JPanel();
    JButton okButton = new JButton("OK");
    JButton cancelButton = new JButton("Cancel");
    okCancelPanel.add(okButton);
    okCancelPanel.add(cancelButton);

    okButton.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            // When accepted the dialog is only hidden. Don't
            // dispose it here, because isDisplayable() is used
            // to find out that OK was pressed!
            _parametersDialog.setVisible(false);
          }});

    cancelButton.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            // When cancelled the dialog is disposed (which is
            // also done when the dialog is closed). It must be
            // disposed here, because isDisplayable() is used
            // to find out that the Dialog was cancelled!
            _parametersDialog.dispose();
          }});

    _parametersDialog.add(okCancelPanel, BorderLayout.SOUTH);
    _parametersDialog.getRootPane().setDefaultButton(okButton);
    _parametersDialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
    _parametersDialog.pack();
    _parametersDialog.setMinimumSize(_parametersDialog.getPreferredSize());
    _parametersDialog.setMaximumSize(
        new Dimension(
            Integer.MAX_VALUE,
            _parametersDialog.getPreferredSize().height));
    _parametersDialog.setLocationRelativeTo(WindowManager.getActiveWindow());

    // Toggle dimensionality
    _dimension.getModel().setValue(3);
    _dimension.getModel().setValue(2);
  }

  @Override
  public void run(String arg) {
    _parametersDialog.setVisible(true);
    if (!_parametersDialog.isDisplayable()) return;

    int nDims = (int)_dimension.getModel().getValue();

    ModelDefinition model = new ModelDefinition();
    model.file = new File(_filename.getText());
    model.id = _id.getText();
    model.name = _name.getText();
    model.description = _description.getText();
    model.inputBlobName = "data3";
    model.padding = "mirror";
    double[] elSize = new double[nDims];
    model.downsampleFactor = new int[nDims];
    model.padInput = new int[nDims];
    model.padOutput = new int[nDims];
    int[] levels = new int[nDims];
    for (int d = 0; d < nDims; ++d) {
      levels[d] = (int)_levels[nDims - d - 1].getModel().getValue();
      elSize[d] = (Double)_elementSizeUm[nDims - d - 1].getModel().getValue();
      model.downsampleFactor[d] = 1 << levels[d];
      model.padInput[d] = 0;
      model.padOutput[d] = -4;
      for (int l = 0; l < (int)_levels[nDims - d - 1].getModel().getValue();
           ++l) {
        model.padInput[d] = model.padInput[d] * 2 + 2 + 2;
        model.padOutput[d] = model.padOutput[d] * 2 - 2 - 2;
      }
    }
    model.setElementSizeUm(elSize);
    model.diskRadiusPx =
        ((Integer)_diskRadiusPx.getModel().getValue()).intValue();
    model.borderWeightFactor =
        ((Double)_borderWeightFactor.getModel().getValue()).floatValue();
    model.borderWeightSigmaPx =
        ((Double)_borderWeightSigmaPx.getModel().getValue()).floatValue();
    model.foregroundBackgroundRatio =
        ((Double)_foregroundBackgroundRatio.getModel().getValue()).floatValue();
    model.sigma1Px = ((Double)_borderSmoothnessSigmaPx.getModel()
                      .getValue()).floatValue();

    // set modelPrototxt and solverPrototxt
    int[] shape = model.getMinimumInputShape();
    int[] grid = new int[nDims];
    int[] mag = new int[nDims];
    for (int d = 0; d < nDims; ++d) {
      grid[d] = (int)_deformationGrid[nDims - d - 1].getModel().getValue();
      mag[d] = (int)_deformationMagnitude[nDims - d - 1].getModel().getValue();
    }
    double[] rotFrom = new double[(nDims == 3) ? 3 : 1];
    double[] rotTo = new double[(nDims == 3) ? 3 : 1];
    if (nDims == 3) {
      for (int d = 0; d < nDims; ++d) {
        rotFrom[d] = (double)_minAngles[d].getModel().getValue();
        rotTo[d] = (double)_maxAngles[d].getModel().getValue();
      }
    }
    else {
      rotFrom[0] = (double)_minAngles[0].getModel().getValue();
      rotTo[0] = (double)_maxAngles[0].getModel().getValue();
    }
    int maxDepth = levels[0];
    for (int d = 1; d < nDims; ++d)
        if (levels[d] > maxDepth) maxDepth = levels[d];
    int nChannels = (int)_nChannels.getModel().getValue();

    // General info
    model.modelPrototxt = "name: '" + model.id + "'\n\n";

    // HDF5 Input Layer
    model.modelPrototxt +=
        "layer { top: 'data' top: 'labels' top: 'weights' top: 'weights2' " +
        "name: 'loaddata' type: 'HDF5Data' hdf5_data_param { source: " +
        "'input_files.txt' batch_size: 1 shuffle: false } include: { " +
        "phase: TRAIN } }\n\n";

    // Create Deformation Layer
    model.modelPrototxt +=
        "layer { bottom: 'weights2' top: 'def' name: 'create_deformation' " +
        "type: 'CreateDeformation'\n" +
        "  create_deformation_param {\n" +
        "    batch_size: 1 " +
        ((nDims == 3) ?
         ("nz: " + shape[0] + " ny: " + shape[1] + " nx: " + shape[2]) :
         ("ny: " + shape[0] + " nx: " + shape[1])) +
        " ncomponents: " + nDims + "\n" +
        "    random_elastic_grid_spacing     {";
    for (int d = 0; d < nDims; ++d)
        model.modelPrototxt += " v: " + grid[d];
    model.modelPrototxt += " }\n" +
        "    random_elastic_deform_magnitude {";
    for (int d = 0; d < nDims; ++d)
        model.modelPrototxt += " v: " + mag[d];
    model.modelPrototxt += " }\n" +
        "    random_offset_range_from_pdf:   true\n";
    if (_mirroring.isSelected()) {
      model.modelPrototxt += "    random_mirror_flag {";
      for (int d = 0; d < nDims; ++d)
          model.modelPrototxt += " v: 1";
      model.modelPrototxt += " }\n";
    }
    model.modelPrototxt += "    random_offset_from              {";
    for (int d = 0; d < nDims; ++d)
        model.modelPrototxt += " v: -" + model.downsampleFactor[d] / 2;
    model.modelPrototxt += " }\n" +
        "    random_offset_to              {";
    for (int d = 0; d < nDims; ++d)
        model.modelPrototxt += " v: " + model.downsampleFactor[d] / 2;
    model.modelPrototxt += " }\n" +
        "    random_rotate_from              {";
    for (int d = 0; d < rotFrom.length; ++d)
        model.modelPrototxt += " v: " + rotFrom[d];
    model.modelPrototxt += " }\n" +
        "    random_rotate_to                {";
    for (int d = 0; d < rotTo.length; ++d)
        model.modelPrototxt += " v: " + rotTo[d];
    model.modelPrototxt += " }\n" +
        "  } include: { phase: TRAIN }\n" +
        "}\n\n";

    // Apply Deformation Layer
    model.modelPrototxt +=
        "layer { bottom: 'data'  bottom: 'def' top: 'data2' " +
        "name: 'def_data-data2'    type: 'ApplyDeformation'  " +
        "apply_deformation_param { interpolation: 'linear' " +
        "extrapolation: 'mirror' } include: { phase: TRAIN } }\n";

    // Value Augmentation Layer
    model.modelPrototxt +=
        "layer { bottom: 'data2'               top: 'data3' " +
        "name: 'augm_data2-data3'  type: 'ValueAugmentation'  " +
        "value_augmentation_param {" +
        " black_from: " + (double)_minValueRange[0].getModel().getValue() +
        " black_to: " + (double)_minValueRange[1].getModel().getValue() +
        " slope_min: " + (double)_slopeRange[0].getModel().getValue() +
        " slope_max: " + (double)_slopeRange[1].getModel().getValue() +
        " white_from: " + (double)_maxValueRange[0].getModel().getValue() +
        " white_to: " + (double)_maxValueRange[1].getModel().getValue() +
        " } include: { phase: TRAIN } }\n\n";

    // Value Transformation Layer
    model.modelPrototxt +=
        "layer { bottom: 'data3'               top: 'd0a'   " +
        "name: 'trafo_data3-d0a'   type: 'ValueTransformation' " +
        "value_transformation_param { offset { v: -0.5 } } }\n\n";

    // Core U-Net
    // Analysis path
    for (int l = 0; l < maxDepth; ++l) {
      int[] convShape = new int[nDims];
      int[] poolShape = new int[nDims];
      for (int d = 0; d < nDims; ++d) {
        convShape[d] = (maxDepth - levels[d] - l > 0) ? 1 : 3;
        poolShape[d] = (maxDepth - levels[d] - l > 0) ? 1 : 2;
      }
      model.modelPrototxt +=
          "layer { bottom: 'd" + l + "a'                 " +
          "top: 'd" + l + "b'   name: 'conv_d" + l + "a-b'        " +
          "type: 'Convolution'   param { lr_mult: 1 decay_mult: 1 } " +
          "param { lr_mult: 2 decay_mult: 0 }  convolution_param { " +
          "num_output: " + (nChannels << l) + " pad: 0";
      for (int d = 0; d < nDims; ++d)
          model.modelPrototxt += " kernel_size: " + convShape[d];
      model.modelPrototxt +=
          " weight_filler { type: 'msra' } } }\n";
      model.modelPrototxt +=
          "layer { bottom: 'd" + l + "b'                 " +
          "top: 'd" + l + "b'   name: 'relu_d" + l + "b'          " +
          "type: 'ReLU' relu_param { negative_slope: 0.1 } }\n";
      model.modelPrototxt +=
          "layer { bottom: 'd" + l + "b'                 " +
          "top: 'd" + l + "c'   name: 'conv_d" + l + "b-c'        " +
          "type: 'Convolution'   param { lr_mult: 1 decay_mult: 1 } " +
          "param { lr_mult: 2 decay_mult: 0 }  convolution_param { " +
          "num_output: " + (nChannels << l) + " pad: 0";
      for (int d = 0; d < nDims; ++d)
          model.modelPrototxt += " kernel_size: " + convShape[d];
      model.modelPrototxt +=
          " weight_filler { type: 'msra' } } }\n";
      model.modelPrototxt +=
          "layer { bottom: 'd" + l + "c'                 " +
          "top: 'd" + l + "c'   name: 'relu_d" + l + "c'          " +
          "type: 'ReLU' relu_param { negative_slope: 0.1 } }\n";
      model.modelPrototxt +=
          "layer { bottom: 'd" + l + "c'                 " +
          "top: 'd" + (l + 1) + "a'   " +
          "name: 'pool_d" + l + "c-" + (l + 1) + "a'       " +
          "type: 'Pooling' pooling_param { pool: MAX";
      for (int d = 0; d < nDims; ++d)
          model.modelPrototxt += " kernel_size: " + poolShape[d];
      for (int d = 0; d < nDims; ++d)
          model.modelPrototxt += " stride: " + poolShape[d];
      model.modelPrototxt += " } }\n\n";
    }

    // Lowest level
    {
      int l = maxDepth;
      int[] convShape = new int[nDims];
      int[] upconvShape = new int[nDims];
      for (int d = 0; d < nDims; ++d) {
        convShape[d] = (-levels[d] > 0) ? 1 : 3;
        upconvShape[d] = (-levels[d] > 0) ? 1 : 2;
      }
      model.modelPrototxt +=
          "layer { bottom: 'd" + l + "a'                 " +
          "top: 'd" + l + "b'   name: 'conv_d" + l + "a-b'        " +
          "type: 'Convolution'   param { lr_mult: 1 decay_mult: 1 } " +
          "param { lr_mult: 2 decay_mult: 0 }  convolution_param { " +
          "num_output: " + (nChannels << l) + " pad: 0";
      for (int d = 0; d < nDims; ++d)
          model.modelPrototxt += " kernel_size: " + convShape[d];
      model.modelPrototxt +=
          " weight_filler { type: 'msra' } } }\n";
      model.modelPrototxt +=
          "layer { bottom: 'd" + l + "b'                 " +
          "top: 'd" + l + "b'   name: 'relu_d" + l + "b'          " +
          "type: 'ReLU' relu_param { negative_slope: 0.1 } }\n";
      model.modelPrototxt +=
          "layer { bottom: 'd" + l + "b'                 " +
          "top: 'd" + l + "c'   name: 'conv_d" + l + "b-c'        " +
          "type: 'Convolution'   param { lr_mult: 1 decay_mult: 1 } " +
          "param { lr_mult: 2 decay_mult: 0 }  convolution_param { " +
          "num_output: " + (nChannels << l) + " pad: 0";
      for (int d = 0; d < nDims; ++d)
          model.modelPrototxt += " kernel_size: " + convShape[d];
      model.modelPrototxt +=
          " weight_filler { type: 'msra' } } }\n";
      model.modelPrototxt +=
          "layer { bottom: 'd" + l + "c'                 " +
          "top: 'd" + l + "c'   name: 'relu_d" + l + "c'          " +
          "type: 'ReLU' relu_param { negative_slope: 0.1 } }\n";
      if (l > 0) {
        model.modelPrototxt +=
            "layer { bottom: 'd" + l + "c'                 " +
            "top: 'u" + (l - 1) + "a'   " +
            "name: 'upconv_d" + l + "c_u" + (l - 1) + "a'    " +
            "type: 'Deconvolution' param { lr_mult: 1 decay_mult: 1 } " +
            "param { lr_mult: 2 decay_mult: 0 }  convolution_param " +
            "{ num_output: " + (nChannels << (l - 1)) + " pad: 0";
        for (int d = 0; d < nDims; ++d)
            model.modelPrototxt += " kernel_size: " + upconvShape[d];
        for (int d = 0; d < nDims; ++d)
            model.modelPrototxt += " stride: " + upconvShape[d];
        model.modelPrototxt += " weight_filler { type: 'msra' } } }\n\n";
        model.modelPrototxt +=
            "layer { bottom: 'u" + (l - 1) + "a'                 " +
            "top: 'u" + (l - 1) + "a'   name: 'relu_u" + l + "a'          " +
            "type: 'ReLU' relu_param { negative_slope: 0.1 } }\n";
      }
    }
    // Synthesis path
    for (int l = maxDepth - 1; l >= 0; --l) {
      int[] convShape = new int[nDims];
      int[] upconvShape = new int[nDims];
      for (int d = 0; d < nDims; ++d) {
        convShape[d] = (maxDepth - levels[d] - l > 0) ? 1 : 3;
        upconvShape[d] = (maxDepth - levels[d] - l > -1) ? 1 : 2;
      }
      model.modelPrototxt +=
          "layer { bottom: 'u" + l + "a'   bottom: 'd" + l + "c' "+
          "top: 'u" + l + "b'   name: 'concat_d" + l + "c_u" + l + "a-b'  " +
          "type: 'Concat' }\n";
      model.modelPrototxt +=
          "layer { bottom: 'u" + l + "b'                 " +
          "top: 'u" + l + "c'   name: 'conv_u" + l + "b-c'        " +
          "type: 'Convolution'   param { lr_mult: 1 decay_mult: 1 } " +
          "param { lr_mult: 2 decay_mult: 0 }  convolution_param { " +
          "num_output: " + (nChannels << l) + " pad: 0";
      for (int d = 0; d < nDims; ++d)
          model.modelPrototxt += " kernel_size: " + convShape[d];
      model.modelPrototxt +=
          " weight_filler { type: 'msra' } } }\n";
      model.modelPrototxt +=
          "layer { bottom: 'u" + l + "c'                 " +
          "top: 'u" + l + "c'   name: 'relu_u" + l + "c'          " +
          "type: 'ReLU' relu_param { negative_slope: 0.1 } }\n";
      model.modelPrototxt +=
          "layer { bottom: 'u" + l + "c'                 " +
          "top: 'u" + l + "d'   name: 'conv_u" + l + "c-d'        " +
          "type: 'Convolution'   param { lr_mult: 1 decay_mult: 1 } " +
          "param { lr_mult: 2 decay_mult: 0 }  convolution_param { " +
          "num_output: " + (nChannels << l) + " pad: 0";
      for (int d = 0; d < nDims; ++d)
          model.modelPrototxt += " kernel_size: " + convShape[d];
      model.modelPrototxt +=
          " weight_filler { type: 'msra' } } }\n";
      model.modelPrototxt +=
          "layer { bottom: 'u" + l + "d'                 " +
          "top: 'u" + l + "d'   name: 'relu_u" + l + "d'          " +
          "type: 'ReLU' relu_param { negative_slope: 0.1 } }\n";
      if (l > 0) {
        model.modelPrototxt +=
            "layer { bottom: 'u" + l + "d'                 " +
            "top: 'u" + (l - 1) + "a'   " +
            "name: 'upconv_u" + l + "d_u" + (l - 1) + "a'    " +
            "type: 'Deconvolution' param { lr_mult: 1 decay_mult: 1 } " +
            "param { lr_mult: 2 decay_mult: 0 }  convolution_param " +
            "{ num_output: " + (nChannels << (l - 1)) + " pad: 0";
        for (int d = 0; d < nDims; ++d)
            model.modelPrototxt += " kernel_size: " + upconvShape[d];
        for (int d = 0; d < nDims; ++d)
            model.modelPrototxt += " stride: " + upconvShape[d];
        model.modelPrototxt += " weight_filler { type: 'msra' } } }\n\n";
        model.modelPrototxt +=
            "layer { bottom: 'u" + (l - 1) + "a'                 " +
            "top: 'u" + (l - 1) + "a'   name: 'relu_u" + l + "a'          " +
            "type: 'ReLU' relu_param { negative_slope: 0.1 } }\n";
      }
    }

    // Mapping to number of classes
    if (maxDepth > 0)
        model.modelPrototxt +=
            "layer { bottom: 'u0d'                 top: 'score' " +
            "name: 'conv_u0d-score'    type: 'Convolution'   " +
            "param { lr_mult: 1 decay_mult: 1 } " +
            "param { lr_mult: 2 decay_mult: 0 }  convolution_param { " +
            "num_output: 2 pad: 0 kernel_size: 1 " +
            "weight_filler { type: 'msra' } } }\n\n";
    else
        model.modelPrototxt +=
            "layer { bottom: 'd0c'                 top: 'score' " +
            "name: 'conv_d0c-score'    type: 'Convolution'   " +
            "param { lr_mult: 1 decay_mult: 1 } " +
            "param { lr_mult: 2 decay_mult: 0 }  convolution_param { " +
            "num_output: 2 pad: 0 kernel_size: 1 " +
            "weight_filler { type: 'msra' } } }\n\n";

    model.modelPrototxt +=
        "layer { bottom: 'labels' bottom: 'def' top: 'labelcrop'  " +
        "name: 'def_label-crop'   type: 'ApplyDeformation'  " +
        "apply_deformation_param { interpolation: 'nearest' " +
        "extrapolation: 'mirror' output_shape_from: 'score'} " +
        "include: { phase: TRAIN } }\n";
    model.modelPrototxt +=
        "layer { bottom: 'weights' bottom: 'def' top: 'weightcrop'  " +
        "name: 'def_weight-crop'   type: 'ApplyDeformation'  " +
        "apply_deformation_param { interpolation: 'linear' " +
        "extrapolation: 'mirror' output_shape_from: 'score'} " +
        "include: { phase: TRAIN } }\n";
    model.modelPrototxt +=
        "layer { bottom: 'score' bottom: 'labelcrop' bottom: 'weightcrop' " +
        "top: 'loss'  name: 'loss'   type: 'SoftmaxWithLoss' " +
        "include: { phase: TRAIN } }\n";

    model.solverPrototxt =
        "net: '" + _id.getText() + ".prototxt'\n" +
        "base_lr:       0.00001\n" +
        "momentum:      0.9\n" +
        "momentum2:     0.999\n" +
        "lr_policy:     'fixed'\n" +
        "max_iter:      600000\n" +
        "display:       1\n" +
        "snapshot:      10000\n" +
        "snapshot_prefix: 'snapshot'\n" +
        "snapshot_format: HDF5\n" +
        "type: 'Adam'\n" +
        "solver_mode:   GPU\n" +
        "debug_info:    0\n";

    Prefs.set("unet.newModel.id", model.id);
    Prefs.set("unet.newModel.name", model.name);
    Prefs.set("unet.newModel.description", model.description);
    Prefs.set("unet.newModel.filename", model.file.getAbsolutePath());
    Prefs.set("unet.newModel.dimension", nDims);
    Prefs.set("unet.newModel.levelsX",
              (int)_levels[0].getModel().getValue());
    Prefs.set("unet.newModel.levelsY",
              (int)_levels[1].getModel().getValue());
    Prefs.set("unet.newModel.levelsZ",
              (int)_levels[2].getModel().getValue());
    Prefs.set("unet.newModel.nChannels", nChannels);
    Prefs.set("unet.newModel.elSizeX",
              (Double)_elementSizeUm[0].getModel().getValue());
    Prefs.set("unet.newModel.elSizeY",
              (Double)_elementSizeUm[1].getModel().getValue());
    Prefs.set("unet.newModel.elSizeZ",
              (Double)_elementSizeUm[2].getModel().getValue());
    Prefs.set("unet.newModel.borderWeightFactor", model.borderWeightFactor);
    Prefs.set("unet.newModel.borderWeightSigmaPx", model.borderWeightSigmaPx);
    Prefs.set("unet.newModel.foregroundBackgroundRatio",
              model.foregroundBackgroundRatio);
    Prefs.set("unet.newModel.borderSmoothnessSigmaPx", model.sigma1Px);
    Prefs.set("unet.newModel.minAnglesPhi",
              (double)_minAngles[0].getModel().getValue());
    Prefs.set("unet.newModel.minAnglesTheta",
              (double)_minAngles[1].getModel().getValue());
    Prefs.set("unet.newModel.minAnglesPsi",
              (double)_minAngles[2].getModel().getValue());
    Prefs.set("unet.newModel.maxAnglesPhi",
              (double)_maxAngles[0].getModel().getValue());
    Prefs.set("unet.newModel.maxAnglesTheta",
              (double)_maxAngles[1].getModel().getValue());
    Prefs.set("unet.newModel.maxAnglesPsi",
              (double)_maxAngles[2].getModel().getValue());
    Prefs.set("unet.newModel.mirroring", _mirroring.isSelected());
    Prefs.set("unet.newModel.deformationGridX",
              (int)_deformationGrid[0].getModel().getValue());
    Prefs.set("unet.newModel.deformationGridY",
              (int)_deformationGrid[1].getModel().getValue());
    Prefs.set("unet.newModel.deformationGridZ",
              (int)_deformationGrid[2].getModel().getValue());
    Prefs.set("unet.newModel.deformationMagnitudeX",
              (int)_deformationMagnitude[0].getModel().getValue());
    Prefs.set("unet.newModel.deformationMagnitudeY",
              (int)_deformationMagnitude[1].getModel().getValue());
    Prefs.set("unet.newModel.deformationMagnitudeZ",
              (int)_deformationMagnitude[2].getModel().getValue());
    Prefs.set("unet.newModel.minValueRangeStart",
              (double)_minValueRange[0].getModel().getValue());
    Prefs.set("unet.newModel.minValueRangeEnd",
              (double)_minValueRange[1].getModel().getValue());
    Prefs.set("unet.newModel.maxValueRangeStart",
              (double)_maxValueRange[0].getModel().getValue());
    Prefs.set("unet.newModel.maxValueRangeEnd",
              (double)_maxValueRange[1].getModel().getValue());
    Prefs.set("unet.newModel.slopeStart",
              (double)_slopeRange[0].getModel().getValue());
    Prefs.set("unet.newModel.slopeEnd",
              (double)_slopeRange[1].getModel().getValue());

    try {
      model.save();
      IJ.showMessage("Model has been saved to " + model.file.getAbsolutePath());
    }
    catch (HDF5Exception|IOException e) {
      IJ.log("Could not save model to " + model.file.getAbsolutePath() +
             ".\n" + e.getMessage());
      IJ.error("Could not save model to " + model.file.getAbsolutePath() +
               ".\n" + e.getMessage());
    }
  }

};
