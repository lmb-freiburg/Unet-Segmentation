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

import ij.Prefs;
import ij.IJ;

import java.io.*;
import java.util.*;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.event.*;

// HDF5 stuff
import ncsa.hdf.hdf5lib.exceptions.HDF5Exception;
import ch.systemsx.cisd.hdf5.HDF5FloatStorageFeatures;
import ch.systemsx.cisd.hdf5.HDF5Factory;
import ch.systemsx.cisd.hdf5.IHDF5Writer;
import ch.systemsx.cisd.hdf5.IHDF5WriterConfigurator;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
import ch.systemsx.cisd.hdf5.HDF5DataSetInformation;
import ch.systemsx.cisd.base.mdarray.MDFloatArray;

public class ModelDefinition {

  private Job _job = null;
  private int[] _minOutTileShape = null;
  private int _nDims = -1;

  public File file = null;
  public String remoteAbsolutePath = null;
  public String modelPrototxtAbsolutePath = null;
  public String solverPrototxtAbsolutePath = null;
  public String id = "Select model folder ==>";
  public String name = "Select model folder ==>";
  public String description = "Select model folder ==>";
  public String inputBlobName = null;
  public String inputDatasetName = "data";
  public String solverPrototxt = null;
  public String modelPrototxt = null;
  public String padding = null;
  public int normalizationType = 0;
  public int[] downsampleFactor = null;
  public int[] padInput = null;
  public int[] padOutput = null;
  public float[] elementSizeUm = null;
  public int[][] memoryMap = null;
  public float borderWeightFactor = 50.0f;
  public float borderWeightSigmaUm = 3.0f;
  public float foregroundBackgroundRatio = 0.1f;
  public float sigma1Um = 5.0f;

  public String weightFile = null;

  private final JComboBox<String> _tileModeSelector = new JComboBox<String>();
  private final JPanel _tileModePanel = new JPanel(new CardLayout());

  private static final String NTILES = "# Tiles:";
  private static final String GRID = "Grid (tiles):";
  private static final String SHAPE = "Tile shape (px):";
  private static final String MEMORY = "Memory (MB):";

  private JSpinner _nTilesSpinner = null;
  private JSpinner _shapeXSpinner = null;
  private JSpinner _shapeYSpinner = null;
  private JSpinner _shapeZSpinner = null;
  private JSpinner _gridXSpinner = null;
  private JSpinner _gridYSpinner = null;
  private JSpinner _gridZSpinner = null;
  private JSpinner _gpuMemSpinner = null;

  public ModelDefinition() {
    // Prepare empty GUI elements for this model
    _tileModePanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
    _tileModeSelector.addItemListener(
        new ItemListener() {
          @Override
          public void itemStateChanged(ItemEvent e) {
            if (e.getStateChange() == ItemEvent.SELECTED) {
              ((CardLayout)_tileModePanel.getLayout()).show(
                  _tileModePanel, (String)_tileModeSelector.getSelectedItem());
            }
          }});
  }

  public ModelDefinition(Job job) {
    _job = job;

    // Prepare empty GUI elements for this model
    _tileModePanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
    _tileModeSelector.addItemListener(
        new ItemListener() {
          @Override
          public void itemStateChanged(ItemEvent e) {
            if (e.getStateChange() == ItemEvent.SELECTED) {
              ((CardLayout)_tileModePanel.getLayout()).show(
                  _tileModePanel, (String)_tileModeSelector.getSelectedItem());
            }
          }});
  }

  private void _initGUIElements() {
    // Clear and then recreate the GUI elements for this model
    _tileModeSelector.removeAllItems();
    _tileModePanel.removeAll();
    createTileShapeCard();
    if (_job != null && _job instanceof SegmentationJob) {
      if (_nDims == 2) {
        createNTilesCard();
        createTileGridCard();
      }
      if (memoryMap != null) createMemoryCard();
      _tileModeSelector.setSelectedItem(
          (String)Prefs.get("unet_segmentation." + id + ".tilingOption",
                            (String)_tileModeSelector.getSelectedItem()));
    }
  }

  public ModelDefinition duplicate() {
    ModelDefinition dup = new ModelDefinition(_job);
    dup._nDims = _nDims;
    dup._minOutTileShape = _minOutTileShape;
    dup.remoteAbsolutePath = remoteAbsolutePath;
    dup.modelPrototxtAbsolutePath = modelPrototxtAbsolutePath;
    dup.solverPrototxtAbsolutePath = solverPrototxtAbsolutePath;
    dup.id = id;
    dup.name = name;
    dup.description = description;
    dup.inputBlobName = inputBlobName;
    dup.inputDatasetName = inputDatasetName;
    dup.solverPrototxt = solverPrototxt;
    dup.modelPrototxt = modelPrototxt;
    dup.padding = padding;
    dup.normalizationType = normalizationType;
    if (downsampleFactor != null) {
      dup.downsampleFactor = new int[downsampleFactor.length];
      dup.downsampleFactor = Arrays.copyOf(
          downsampleFactor, downsampleFactor.length);
    }
    if (padInput != null) {
      dup.padInput = new int[padInput.length];
      dup.padInput = Arrays.copyOf(padInput, padInput.length);
    }
    if (padOutput != null) {
      dup.padOutput = new int[padOutput.length];
      dup.padOutput = Arrays.copyOf(padOutput, padInput.length);
    }
    if (elementSizeUm != null) {
      dup.elementSizeUm = new float[elementSizeUm.length];
      dup.elementSizeUm = Arrays.copyOf(elementSizeUm, elementSizeUm.length);
    }
    if (memoryMap != null) {
      dup.memoryMap = new int[memoryMap.length][memoryMap[0].length];
      for (int r = 0; r < memoryMap.length; r++)
          dup.memoryMap[r] = Arrays.copyOf(memoryMap[r], memoryMap[r].length);
    }
    dup.borderWeightFactor = borderWeightFactor;
    dup.borderWeightSigmaUm = borderWeightSigmaUm;
    dup.foregroundBackgroundRatio = foregroundBackgroundRatio;
    dup.sigma1Um = sigma1Um;
    dup.weightFile = weightFile;
    dup._initGUIElements();
    if (_nTilesSpinner != null)
        dup._nTilesSpinner.setValue((Integer)_nTilesSpinner.getValue());
    if (_shapeXSpinner != null)
        dup._shapeXSpinner.setValue((Integer)_shapeXSpinner.getValue());
    if (_shapeYSpinner != null)
        dup._shapeYSpinner.setValue((Integer)_shapeYSpinner.getValue());
    if (_shapeZSpinner != null)
        dup._shapeZSpinner.setValue((Integer)_shapeZSpinner.getValue());
    if (_gridXSpinner != null)
        dup._gridXSpinner.setValue((Integer)_gridXSpinner.getValue());
    if (_gridYSpinner != null)
        dup._gridYSpinner.setValue((Integer)_gridYSpinner.getValue());
    if (_gridZSpinner != null)
        dup._gridZSpinner.setValue((Integer)_gridZSpinner.getValue());
    if (_gpuMemSpinner != null)
        dup._gpuMemSpinner.setValue((Integer)_gpuMemSpinner.getValue());
    dup._tileModeSelector.setSelectedItem(_tileModeSelector.getSelectedItem());
    return dup;
  }

  private void _load(File inputFile) throws HDF5Exception {
    IHDF5Reader reader = HDF5Factory.configureForReading(inputFile).reader();
    file = inputFile;
    id = reader.string().read("/.unet-ident");
    name = reader.string().read("/unet_param/name");
    description = reader.string().read("/unet_param/description");
    inputBlobName = reader.string().read("/unet_param/input_blob_name");
    try {
      inputDatasetName = reader.string().read("/unet_param/input_dataset_name");
    }
    catch (HDF5Exception e) {}
    solverPrototxt = reader.string().read("/solver_prototxt");
    modelPrototxt = reader.string().read("/model_prototxt");
    padding = reader.string().read("/unet_param/padding");
    normalizationType = reader.int32().read("/unet_param/normalization_type");
    downsampleFactor = reader.int32().readArray("/unet_param/downsampleFactor");
    padInput = reader.int32().readArray("/unet_param/padInput");
    padOutput = reader.int32().readArray("/unet_param/padOutput");
    elementSizeUm = reader.float32().readArray("/unet_param/element_size_um");
    try {
      borderWeightFactor = reader.float32().read(
          "/unet_param/pixelwise_loss_weights/borderWeightFactor");
      borderWeightSigmaUm = reader.float32().read(
          "/unet_param/pixelwise_loss_weights/borderWeightSigmaUm");
      foregroundBackgroundRatio = reader.float32().read(
          "/unet_param/pixelwise_loss_weights/foregroundBackgroundRatio");
      sigma1Um = reader.float32().read(
          "/unet_param/pixelwise_loss_weights/sigma1_um");
    }
    catch (HDF5Exception e) {}

    try {
      memoryMap = reader.int32().readMatrix(
          "/unet_param/mapInputNumPxGPUMemMB");
    }
    catch (HDF5Exception e) {
      memoryMap = null;
    }
    reader.close();

    _nDims = elementSizeUm.length;

    // Convert scalar parameters to vectors
    if (downsampleFactor.length == 1) {
      int dsFactor = downsampleFactor[0];
      downsampleFactor = new int[_nDims];
      for (int d = 0; d < _nDims; d++)
          downsampleFactor[d] = dsFactor;
    }
    if (padInput.length == 1) {
      int pad = padInput[0];
      padInput = new int[_nDims];
      for (int d = 0; d < _nDims; d++) padInput[d] = pad;
    }
    if (padOutput.length == 1) {
      int pad = padOutput[0];
      padOutput = new int[_nDims];
      for (int d = 0; d < _nDims; d++) padOutput[d] = pad;
    }

    // Compute minimum output tile shape
    _minOutTileShape = new int[_nDims];
    for (int d = 0; d < _nDims; d++) {
      _minOutTileShape[d] = padOutput[d];
      while (_minOutTileShape[d] < 0)
          _minOutTileShape[d] += downsampleFactor[d];
    }

    weightFile = Prefs.get("unet_segmentation." + id + ".weightFile", "");
  }

  public boolean isValid() {
    return _nDims > 0;
  }

  public int nDims() {
    return _nDims;
  }

  public JComponent tileModeSelector() {
    return isValid() ? _tileModeSelector : new JPanel();
  }

  public JPanel tileModePanel() {
    return _tileModePanel;
  }

  public int[] getOutputTileShape(int[] inputTileShape) {
    int[] res = new int[_nDims];
    for (int d = 0; d < _nDims; d++)
        res[d] = inputTileShape[d] - (padInput[d] - padOutput[d]);
    return res;
  }

  public int[] getInputTileShape(int[] outputTileShape) {
    int[] res = new int[_nDims];
    for (int d = 0; d < _nDims; d++)
        res[d] = outputTileShape[d] + (padInput[d] - padOutput[d]);
    return res;
  }

  private void createTileShapeCard() {
    JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    panel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
    ((FlowLayout)panel.getLayout()).setAlignOnBaseline(true);

    panel.add(new JLabel(" x: "));
    {
      int dsFactor = downsampleFactor[_nDims - 1];
      final int minValue =
          (_job != null && _job instanceof SegmentationJob) ?
          _minOutTileShape[_nDims - 1] :
          getInputTileShape(_minOutTileShape)[_nDims - 1];
      _shapeXSpinner = new JSpinner(
          new SpinnerNumberModel(
              (int)Prefs.get(
                  "unet_segmentation." + id + ".tileShapeX",
                  minValue + 10 * dsFactor),
              minValue, (int)Integer.MAX_VALUE, dsFactor));
      _shapeXSpinner.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
              int value = (Integer)_shapeXSpinner.getValue();
              int nextValidSize = Math.max(
                  (int)Math.floor((value - minValue) / dsFactor) *
                  dsFactor + minValue, minValue);
              if (nextValidSize == value) return;
              _shapeXSpinner.setValue(nextValidSize);
            }
          });
    }
    panel.add(_shapeXSpinner);

    panel.add(new JLabel(" y: "));
    {
      int dsFactor = downsampleFactor[_nDims - 2];
      final int minValue =
          (_job != null && _job instanceof SegmentationJob) ?
          _minOutTileShape[_nDims - 2] :
          getInputTileShape(_minOutTileShape)[_nDims - 2];
      _shapeYSpinner = new JSpinner(
          new SpinnerNumberModel(
              (int)Prefs.get("unet_segmentation." + id + ".tileShapeY",
                             minValue + 10 * dsFactor),
              minValue, (int)Integer.MAX_VALUE, dsFactor));
      _shapeYSpinner.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
              int value = (Integer)_shapeYSpinner.getValue();
              int nextValidSize = Math.max(
                  (int)Math.floor((value - minValue) / dsFactor) *
                  dsFactor + minValue, minValue);
              if (nextValidSize == value) return;
              _shapeYSpinner.setValue(nextValidSize);
            }
          });
    }
    panel.add(_shapeYSpinner);

    if (_nDims == 3) {
      panel.add(new JLabel(" z: "));
      int dsFactor = downsampleFactor[_nDims - 3];
      final int minValue =
          (_job != null && _job instanceof SegmentationJob) ?
          _minOutTileShape[_nDims - 3] :
          getInputTileShape(_minOutTileShape)[_nDims - 3];
      _shapeZSpinner = new JSpinner(
          new SpinnerNumberModel(
              (int)Prefs.get("unet_segmentation." + id + ".tileShapeZ",
                             minValue + 10 * dsFactor),
              minValue, (int)Integer.MAX_VALUE, dsFactor));
      _shapeZSpinner.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
              int value = (Integer)_shapeZSpinner.getValue();
              int nextValidSize = Math.max(
                  (int)Math.floor((value - minValue) / dsFactor) *
                  dsFactor + minValue, minValue);
              if (nextValidSize == value) return;
              _shapeZSpinner.setValue(nextValidSize);
            }
          });
      panel.add(_shapeZSpinner);
    }
    _tileModePanel.add(panel, SHAPE);
    _tileModeSelector.addItem(SHAPE);
  }

  private void createNTilesCard() {
    _nTilesSpinner = new JSpinner(
        new SpinnerNumberModel(
            (int)Prefs.get("unet_segmentation." + id + ".nTiles", 1), 1,
            (int)Integer.MAX_VALUE, 1));
    _tileModePanel.add(_nTilesSpinner, NTILES);
    _tileModeSelector.addItem(NTILES);
  }

  private void createTileGridCard() {
    JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    panel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
    ((FlowLayout)panel.getLayout()).setAlignOnBaseline(true);
    panel.add(new JLabel(" x: "));
    _gridXSpinner = new JSpinner(
        new SpinnerNumberModel(
            (int)Prefs.get("unet_segmentation." + id + ".tileGridX", 5), 1,
            (int)Integer.MAX_VALUE, 1));
    panel.add(_gridXSpinner);
    panel.add(new JLabel(" y: "));
    _gridYSpinner = new JSpinner(
        new SpinnerNumberModel(
            (int)Prefs.get("unet_segmentation." + id + ".tileGridY", 5), 1,
            (int)Integer.MAX_VALUE, 1));
    panel.add(_gridYSpinner);
    if (_nDims == 3) {
      panel.add(new JLabel(" z: "));
      _gridZSpinner = new JSpinner(
          new SpinnerNumberModel(
              (int)Prefs.get("unet_segmentation." + id + ".tileGridZ", 5), 1,
              (int)Integer.MAX_VALUE, 1));
      panel.add(_gridZSpinner);
    }
    _tileModePanel.add(panel, GRID);
    _tileModeSelector.addItem(GRID);
  }

  private void createMemoryCard() {
    _gpuMemSpinner = new JSpinner(
        new SpinnerNumberModel(
            (int)Prefs.get("unet_segmentation." + id + ".GPUMemoryMB", 1000), 1,
            (int)Integer.MAX_VALUE, 1));
    _tileModePanel.add(_gpuMemSpinner, MEMORY);
    _tileModeSelector.addItem(MEMORY);
  }

  public void load(File inputFile) throws HDF5Exception {
    _load(inputFile);
    _initGUIElements();
  }

  public void load() throws HDF5Exception {
    load(file);
  }

  public void saveModelPrototxt(File f) throws IOException {
    BufferedWriter writer = new BufferedWriter(new FileWriter(f));
    writer.write(modelPrototxt);
    writer.flush();
  }

  public void saveSolverPrototxt(File f) throws IOException {
    BufferedWriter writer = new BufferedWriter(new FileWriter(f));
    writer.write(solverPrototxt);
    writer.flush();
  }

  public void save(File outputFile) throws HDF5Exception {
    IHDF5Writer writer =
        HDF5Factory.configure(outputFile).
        syncMode(IHDF5WriterConfigurator.SyncMode.SYNC_BLOCK)
        .useSimpleDataSpaceForAttributes()
        .useUTF8CharacterEncoding().writer();
    file = outputFile;
    writer.string().write("/.unet-ident", id);
    writer.string().write("/unet_param/name", name);
    writer.string().write("/unet_param/description", description);
    writer.string().write("/unet_param/input_blob_name", inputBlobName);
    writer.string().write("/unet_param/input_dataset_name", inputDatasetName);
    writer.string().write("/solver_prototxt", solverPrototxt);
    writer.string().write("/model_prototxt", modelPrototxt);
    writer.string().write("/unet_param/padding", padding);
    writer.int32().write("/unet_param/normalization_type", normalizationType);
    writer.int32().writeArray("/unet_param/downsampleFactor", downsampleFactor);
    writer.int32().writeArray("/unet_param/padInput", padInput);
    writer.int32().writeArray("/unet_param/padOutput", padOutput);
    writer.float32().writeArray("/unet_param/element_size_um", elementSizeUm);
    writer.float32().write(
        "/unet_param/pixelwise_loss_weights/borderWeightFactor",
        borderWeightFactor);
    writer.float32().write(
          "/unet_param/pixelwise_loss_weights/borderWeightSigmaUm",
          borderWeightSigmaUm);
    writer.float32().write(
        "/unet_param/pixelwise_loss_weights/foregroundBackgroundRatio",
        foregroundBackgroundRatio);
    writer.float32().write(
        "/unet_param/pixelwise_loss_weights/sigma1_um", sigma1Um);
    if (memoryMap != null && memoryMap.length == 2)
        writer.int32().writeMatrix(
            "/unet_param/mapInputNumPxGPUMemMB", memoryMap);
    writer.close();
  }

  public void save() throws HDF5Exception {
    save(file);
  }

  public void setFromTilingParameterString(String tilingParameter) {
    String[] arg = tilingParameter.split("=");
    if (arg[0].equals(NTILES)) {
      _tileModeSelector.setSelectedItem(NTILES);
      _nTilesSpinner.setValue(Integer.valueOf(arg[1]));
      return;
    }
    if (arg[0].equals(GRID)) {
      _tileModeSelector.setSelectedItem(GRID);
      String[] st = arg[1].split("x");
      if (st.length > 0) _gridXSpinner.setValue(Integer.valueOf(st[0]));
      else _gridXSpinner.setValue(1);
      if (st.length > 1) _gridYSpinner.setValue(Integer.valueOf(st[1]));
      else _gridYSpinner.setValue(1);
      if (_nDims == 3) {
        if (st.length > 2) _gridZSpinner.setValue(Integer.valueOf(st[2]));
        else _gridZSpinner.setValue(1);
      }
      return;
    }
    if (arg[0].equals(SHAPE)) {
      _tileModeSelector.setSelectedItem(SHAPE);
      String[] st = arg[1].split("x");
      if (st.length > 0) _shapeXSpinner.setValue(Integer.valueOf(st[0]));
      else _shapeXSpinner.setValue(
          (Integer)(
              (SpinnerNumberModel)_shapeXSpinner.getModel()).getMinimum());
      if (st.length > 1) _shapeYSpinner.setValue(Integer.valueOf(st[1]));
      else _shapeYSpinner.setValue(
          (Integer)(
              (SpinnerNumberModel)_shapeYSpinner.getModel()).getMinimum());
      if (_nDims == 3) {
        if (st.length > 2)
            _shapeZSpinner.setValue(Integer.valueOf(st[2]));
        else _shapeZSpinner.setValue(
            (Integer)(
                (SpinnerNumberModel)_shapeXSpinner.getModel()).getMinimum());
      }
      return;
    }
    if (arg[0].equals(MEMORY)) {
      _tileModeSelector.setSelectedItem(MEMORY);
      _gpuMemSpinner.setValue(Integer.valueOf(arg[1]));
      return;
    }
  }

  public String getTilingParameterString() {
    if (((String)_tileModeSelector.getSelectedItem()).equals(NTILES))
        return NTILES + "=" + (Integer)_nTilesSpinner.getValue();
    if (((String)_tileModeSelector.getSelectedItem()).equals(GRID))
        return GRID + "=" + (Integer)_gridXSpinner.getValue() + "x" +
            (Integer)_gridYSpinner.getValue() +
            ((_nDims == 3) ?
             ("x" + (Integer)_gridZSpinner.getValue()) : "");
    if (((String)_tileModeSelector.getSelectedItem()).equals(SHAPE))
        return SHAPE + "=" + (Integer)_shapeXSpinner.getValue() + "x" +
            (Integer)_shapeYSpinner.getValue() +
            ((_nDims == 3) ?
             ("x" + (Integer)_shapeZSpinner.getValue()) : "");
    if (((String)_tileModeSelector.getSelectedItem()).equals(MEMORY))
        return MEMORY + "=" + (Integer)_gpuMemSpinner.getValue();
    throw new UnsupportedOperationException(
        "Cannot handle unknown tiling mode '" +
        (String)_tileModeSelector.getSelectedItem() + "'");
  }

  public String getCaffeTilingParameter() throws UnsupportedOperationException {
    if (((String)_tileModeSelector.getSelectedItem()).equals(NTILES))
        return "-n_tiles " + (Integer)_nTilesSpinner.getValue();
    if (((String)_tileModeSelector.getSelectedItem()).equals(GRID))
        return "-n_tiles " +
            ((_nDims == 3) ?
             ((Integer)_gridZSpinner.getValue() + "x") : "") +
            (Integer)_gridYSpinner.getValue() + "x" +
            (Integer)_gridXSpinner.getValue();
    if (((String)_tileModeSelector.getSelectedItem()).equals(SHAPE))
        return "-tile_size " +
            ((_nDims == 3) ?
             ((Integer)_shapeZSpinner.getValue() + "x") : "") +
            (Integer)_shapeYSpinner.getValue() + "x" +
            (Integer)_shapeXSpinner.getValue();
    if (((String)_tileModeSelector.getSelectedItem()).equals(MEMORY))
        return "-gpu_mem_available_MB " +
            (Integer)_gpuMemSpinner.getValue();
    throw new UnsupportedOperationException(
        "Cannot handle unknown tiling mode '" +
        (String)_tileModeSelector.getSelectedItem() + "'");
  }

  public String getProtobufTileShapeString() {
    return ((_nDims == 3) ?
            ("nz: " + (Integer)_shapeZSpinner.getValue()) + " " : "") +
        "ny: " + (Integer)_shapeYSpinner.getValue() +
        " nx: " + (Integer)_shapeXSpinner.getValue();
  }

  public int[] getTileShape() {
    int[] res = new int[_nDims];
    if (_nDims == 3) {
      res[0] = (Integer)_shapeZSpinner.getValue();
      res[1] = (Integer)_shapeYSpinner.getValue();
      res[2] = (Integer)_shapeXSpinner.getValue();
    }
    else {
      res[0] = (Integer)_shapeYSpinner.getValue();
      res[1] = (Integer)_shapeXSpinner.getValue();
    }
    return res;
  }

  public void savePreferences() {
    Prefs.set("unet_segmentation." + id + ".weightFile", weightFile);
    if (_job != null && _job instanceof SegmentationJob)
        Prefs.set("unet_segmentation." + id + ".tilingOption",
                  (String)_tileModeSelector.getSelectedItem());

    if (((String)_tileModeSelector.getSelectedItem()).equals(NTILES)) {
      Prefs.set("unet_segmentation." + id + ".nTiles",
                (Integer)_nTilesSpinner.getValue());
      return;
    }

    if (((String)_tileModeSelector.getSelectedItem()).equals(GRID)) {
      Prefs.set("unet_segmentation." + id + ".tileGridX",
                (Integer)_gridXSpinner.getValue());
      Prefs.set("unet_segmentation." + id + ".tileGridY",
                (Integer)_gridYSpinner.getValue());
      if (_nDims == 3)
          Prefs.set("unet_segmentation." + id + ".tileGridZ",
                    (Integer)_gridZSpinner.getValue());
      return;
    }

    if (((String)_tileModeSelector.getSelectedItem()).equals(SHAPE)) {
      Prefs.set("unet_segmentation." + id + ".tileShapeX",
                (Integer)_shapeXSpinner.getValue());
      Prefs.set("unet_segmentation." + id + ".tileShapeY",
                (Integer)_shapeYSpinner.getValue());
      if (_nDims == 3)
          Prefs.set("unet_segmentation." + id + ".tileShapeZ",
                    (Integer)_shapeZSpinner.getValue());
      return;
    }

    if (((String)_tileModeSelector.getSelectedItem()).equals(MEMORY)) {
      Prefs.set("unet_segmentation." + id + ".GPUMemoryMB",
                (Integer)_gpuMemSpinner.getValue());
      return;
    }

    throw new UnsupportedOperationException(
        "Cannot handle unknown tiling mode '" +
        (String)_tileModeSelector.getSelectedItem() + "'");
  }

  @Override
  public String toString() {
    return name;
  }

};
