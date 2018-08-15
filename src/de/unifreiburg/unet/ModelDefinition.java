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
import ij.Prefs;
import ij.ImagePlus;

import java.io.File;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.FlowLayout;
import java.awt.event.ItemListener;
import java.awt.event.ItemEvent;
import javax.swing.JComponent;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.JSpinner;
import javax.swing.BorderFactory;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ChangeListener;
import javax.swing.event.ChangeEvent;

// HDF5 stuff
import ncsa.hdf.hdf5lib.exceptions.HDF5Exception;
import ch.systemsx.cisd.hdf5.HDF5Factory;
import ch.systemsx.cisd.hdf5.IHDF5Writer;
import ch.systemsx.cisd.hdf5.IHDF5WriterConfigurator;
import ch.systemsx.cisd.hdf5.IHDF5Reader;

import caffe.Caffe;
import com.google.protobuf.TextFormat;
import com.google.protobuf.TextFormat.ParseException;

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
  public int[][] memoryMap = null;
  public float borderWeightFactor = 50.0f;
  public float borderWeightSigmaUm = 3.0f;
  public float foregroundBackgroundRatio = 0.1f;
  public float sigma1Um = 5.0f;
  public String[] classNames = null;

  public String weightFile = null;

  private final JComboBox<String> _tileModeSelector = new JComboBox<String>();
  private final JPanel _tileModePanel = new JPanel(new CardLayout());
  private final JLabel _memoryRequiredLabel =
      new JLabel("Estimated GPU Memory:");
  private final JLabel _memoryRequiredPanel = new JLabel();

  private static final String NTILES = "# Tiles:";
  private static final String GRID = "Grid (tiles):";
  private static final String SHAPE = "Tile shape (px):";
  private static final String MEMORY = "Memory (MB):";

  private JSpinner _nTilesSpinner = null;
  private ChangeListener _nTilesChangeListener = null;
  private JSpinner _shapeXSpinner = null;
  private JSpinner _shapeYSpinner = null;
  private JSpinner _shapeZSpinner = null;
  private ChangeListener _shapeChangeUpdateGridAndNTilesListener = null;
  private ChangeListener _shapeChangeUpdateMemoryListener = null;
  private JSpinner _gridXSpinner = null;
  private JSpinner _gridYSpinner = null;
  private JSpinner _gridZSpinner = null;
  private ChangeListener _gridChangeListener = null;
  private JSpinner _gpuMemSpinner = null;

  private final JPanel _elementSizeUmPanel = new JPanel(
      new FlowLayout(FlowLayout.LEFT, 0, 0));
  private final JSpinner _elSizeXSpinner = new JSpinner(
      new SpinnerNumberModel(0.5, 0.0001, 1000000.0, 0.01));
  private final JSpinner _elSizeYSpinner = new JSpinner(
      new SpinnerNumberModel(0.5, 0.0001, 1000000.0, 0.01));
  private final JSpinner _elSizeZSpinner = new JSpinner(
      new SpinnerNumberModel(0.5, 0.0001, 1000000.0, 0.01));

  public ModelDefinition() {
    this(null);
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
    _elementSizeUmPanel.setBorder(
        BorderFactory.createEmptyBorder(0, 0, 0, 0));
    ((FlowLayout)_elementSizeUmPanel.getLayout()).setAlignOnBaseline(true);
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
          (String)Prefs.get("unet." + id + ".tilingOption",
                            (String)_tileModeSelector.getSelectedItem()));
    }
  }

  public void setElementSizeUm(double[] elSize) {
    if (elSize == null || elSize.length < 2 || elSize.length > 3) return;

    if (elSize.length != _nDims) {

      _nDims = elSize.length;
      _elementSizeUmPanel.removeAll();
      _elementSizeUmPanel.add(new JLabel(" x: "));
      _elementSizeUmPanel.add(_elSizeXSpinner);
      _elementSizeUmPanel.add(new JLabel(" y: "));
      _elementSizeUmPanel.add(_elSizeYSpinner);
      if (_nDims == 3) {
        _elementSizeUmPanel.add(new JLabel(" z: "));
        _elementSizeUmPanel.add(_elSizeZSpinner);
      }
    }

    if (_nDims == 2) {
      _elSizeXSpinner.setValue((double)elSize[1]);
      _elSizeYSpinner.setValue((double)elSize[0]);
    }
    else {
      _elSizeXSpinner.setValue((double)elSize[2]);
      _elSizeYSpinner.setValue((double)elSize[1]);
      _elSizeZSpinner.setValue((double)elSize[0]);
    }
  }

  public double[] elementSizeUm() {
    if (!isValid()) return null;
    double[] res = new double[_nDims];
    if (_nDims == 2) {
      res[0] = ((Double)_elSizeYSpinner.getValue()).doubleValue();
      res[1] = ((Double)_elSizeXSpinner.getValue()).doubleValue();
    }
    else {
      res[0] = ((Double)_elSizeZSpinner.getValue()).doubleValue();
      res[1] = ((Double)_elSizeYSpinner.getValue()).doubleValue();
      res[2] = ((Double)_elSizeXSpinner.getValue()).doubleValue();
    }
    return res;
  }

  public ModelDefinition duplicate() {
    ModelDefinition dup = new ModelDefinition(_job);
    if (_minOutTileShape != null)
        dup._minOutTileShape = Arrays.copyOf(
            _minOutTileShape, _minOutTileShape.length);
    dup._nDims = _nDims;
    dup.file = new File(file.getPath());
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
    if (elementSizeUm() != null) dup.setElementSizeUm(elementSizeUm());
    if (downsampleFactor != null)
        dup.downsampleFactor = Arrays.copyOf(
            downsampleFactor, downsampleFactor.length);
    if (padInput != null)
        dup.padInput = Arrays.copyOf(padInput, padInput.length);
    if (padOutput != null)
        dup.padOutput = Arrays.copyOf(padOutput, padInput.length);
    if (memoryMap != null) {
      dup.memoryMap = new int[memoryMap.length][memoryMap[0].length];
      for (int r = 0; r < memoryMap.length; r++)
          dup.memoryMap[r] = Arrays.copyOf(memoryMap[r], memoryMap[r].length);
    }
    dup.borderWeightFactor = borderWeightFactor;
    dup.borderWeightSigmaUm = borderWeightSigmaUm;
    dup.foregroundBackgroundRatio = foregroundBackgroundRatio;
    dup.sigma1Um = sigma1Um;
    if (classNames != null) {
      dup.classNames = new String[classNames.length];
      for (int i = 0; i < classNames.length; i++)
          dup.classNames[i] = classNames[i];
    }
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
    setElementSizeUm(reader.float64().readArray("/unet_param/element_size_um"));
    downsampleFactor = reader.int32().readArray("/unet_param/downsampleFactor");
    padInput = reader.int32().readArray("/unet_param/padInput");
    padOutput = reader.int32().readArray("/unet_param/padOutput");
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
      classNames = reader.string().readArray("/unet_param/classNames");
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

    weightFile = Prefs.get("unet." + id + ".weightFile", "");
  }

  public boolean isValid() {
    return _nDims > 0;
  }

  public int nDims() {
    return _nDims;
  }

  public JComponent tileModeSelector() {
    return _tileModeSelector;
  }

  public JPanel tileModePanel() {
    return _tileModePanel;
  }

  public JComponent memoryRequiredLabel() {
    return _memoryRequiredLabel;
  }

  public JComponent memoryRequiredPanel() {
    return _memoryRequiredPanel;
  }

  public JPanel elementSizeUmPanel() {
    return _elementSizeUmPanel;
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

    String prefsPrefix = (_job != null && _job instanceof FinetuneJob) ?
        "unet.finetuning." : "unet.segmentation.";

    panel.add(new JLabel(" x: "));
    {
      int dsFactor = downsampleFactor[_nDims - 1];
      final int minValue =
          (_job != null && _job instanceof SegmentationJob) ?
          _minOutTileShape[_nDims - 1] :
          getInputTileShape(_minOutTileShape)[_nDims - 1];
      _shapeXSpinner = new JSpinner(
          new SpinnerNumberModel(
              minValue, minValue, (int)Integer.MAX_VALUE, dsFactor));
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
      _shapeXSpinner.setValue(
          (int)Prefs.get(
              prefsPrefix + id + ".tileShapeX", minValue + 10 * dsFactor));
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
              minValue, minValue, (int)Integer.MAX_VALUE, dsFactor));
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
      _shapeYSpinner.setValue(
          (int)Prefs.get(
              prefsPrefix + id + ".tileShapeY", minValue + 10 * dsFactor));
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
              minValue, minValue, (int)Integer.MAX_VALUE, dsFactor));
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
      _shapeZSpinner.setValue(
          (int)Prefs.get(
              prefsPrefix + id + ".tileShapeZ", minValue + 10 * dsFactor));
      panel.add(_shapeZSpinner);
    }

    _shapeChangeUpdateGridAndNTilesListener = new ChangeListener() {
          @Override
          public void stateChanged(ChangeEvent e) {
            if (_job instanceof SegmentationJob) {
              ImagePlus imp = ((SegmentationJob)_job).image();
              int[] scaledShape = getScaledShape(imp);
              int[] tileShape = getTileShape();
              if (scaledShape == null) return;
              int nTiles = 1;
              int[] grid = new int[_nDims];
              for (int d = 0; d < _nDims; ++d) {
                grid[d] = (int)Math.ceil(
                    (double)scaledShape[d] / (double)tileShape[d]);
                nTiles *= grid[d];
              }

              if (_gridXSpinner != null) {
                _gridXSpinner.removeChangeListener(_gridChangeListener);
                _gridXSpinner.setValue(grid[_nDims - 1]);
                _gridXSpinner.addChangeListener(_gridChangeListener);
              }
              if (_gridYSpinner != null) {
                _gridYSpinner.removeChangeListener(_gridChangeListener);
                _gridYSpinner.setValue(grid[_nDims - 2]);
                _gridYSpinner.addChangeListener(_gridChangeListener);
              }
              if (_nDims == 3 && _gridZSpinner != null) {
                _gridZSpinner.removeChangeListener(_gridChangeListener);
                _gridZSpinner.setValue(grid[0]);
                _gridZSpinner.addChangeListener(_gridChangeListener);
              }
              if (_nTilesSpinner != null) {
                _nTilesSpinner.removeChangeListener(_nTilesChangeListener);
                _nTilesSpinner.setValue(nTiles);
                _nTilesSpinner.addChangeListener(_nTilesChangeListener);
              }
            }
          }
        };

    _shapeChangeUpdateMemoryListener = new ChangeListener() {
          @Override
          public void stateChanged(ChangeEvent e) {
            if (_job instanceof SegmentationJob) {
              long mem = computeMemoryConsumptionInTestPhase(false);
              if (mem != -1) {
                long memCuDNN = computeMemoryConsumptionInTestPhase(true);
                _memoryRequiredPanel.setText(
                    " No cuDNN: " + mem / 1024 / 1024 + " MB     cuDNN: " +
                    memCuDNN / 1024 / 1024 + " MB");
              }
            }
            else if (_job instanceof FinetuneJob) {
              long mem = computeMemoryConsumptionInTrainPhase(false);
              if (mem != -1) {
                long memCuDNN = computeMemoryConsumptionInTrainPhase(true);
                _memoryRequiredPanel.setText(
                    " No cuDNN: " + mem / 1024 / 1024 + " MB     cuDNN: " +
                    memCuDNN / 1024 / 1024 + " MB");
              }
            }
          }
        };

    _shapeXSpinner.addChangeListener(_shapeChangeUpdateGridAndNTilesListener);
    _shapeXSpinner.addChangeListener(_shapeChangeUpdateMemoryListener);
    _shapeYSpinner.addChangeListener(_shapeChangeUpdateGridAndNTilesListener);
    _shapeYSpinner.addChangeListener(_shapeChangeUpdateMemoryListener);
    if (_nDims == 3) {
      _shapeZSpinner.addChangeListener(_shapeChangeUpdateGridAndNTilesListener);
      _shapeZSpinner.addChangeListener(_shapeChangeUpdateMemoryListener);
    }

    // Trigger update of related tiling panels and memory display
    _shapeChangeUpdateGridAndNTilesListener.stateChanged(
        new ChangeEvent(_shapeXSpinner));
    _shapeChangeUpdateMemoryListener.stateChanged(
        new ChangeEvent(_shapeXSpinner));

    _tileModePanel.add(panel, SHAPE);
    _tileModeSelector.addItem(SHAPE);
  }

  private void createNTilesCard() {
    _nTilesSpinner = new JSpinner(
        new SpinnerNumberModel(
            (int)Prefs.get("unet." + id + ".nTiles", 1), 1,
            (int)Integer.MAX_VALUE, 1));

    _nTilesChangeListener = new ChangeListener() {
          @Override
          public void stateChanged(ChangeEvent e) {
            if (!(_job instanceof SegmentationJob)) return;
            ImagePlus imp = ((SegmentationJob)_job).image();

            int[] scaledShape = getScaledShape(imp);
            if (scaledShape == null) return;

            int[] outTileShape = new int[_nDims];
            int nTilesWanted = (Integer)_nTilesSpinner.getValue();
            int nTiles = 1;
            for (int d = 0; d < _nDims; ++d) {
              outTileShape[d] = _minOutTileShape[d];
              nTiles *= (int)Math.ceil(
                  (double)scaledShape[d] / (double)outTileShape[d]);
            }
            int currentDim = 0;
            while (nTiles > nTilesWanted) {
              while (outTileShape[currentDim] >= scaledShape[currentDim])
                  currentDim = (currentDim + 1) % _nDims;
              outTileShape[currentDim] += downsampleFactor[currentDim];
              nTiles = 1;
              for (int d = 0; d < _nDims; ++d)
                  nTiles *= (int)Math.ceil(
                      (double)scaledShape[d] / (double)outTileShape[d]);
              currentDim = (currentDim + 1) % _nDims;
            }

            // Update tile grid spinners
            if (_gridXSpinner != null) {
              _gridXSpinner.removeChangeListener(_gridChangeListener);
              _gridXSpinner.setValue(
                  (int)Math.ceil((double)scaledShape[_nDims - 1] /
                                 (double)outTileShape[_nDims - 1]));
              _gridXSpinner.addChangeListener(_gridChangeListener);
            }
            if (_gridYSpinner != null) {
              _gridYSpinner.removeChangeListener(_gridChangeListener);
              _gridYSpinner.setValue(
                  (int)Math.ceil((double)scaledShape[_nDims - 2] /
                                 (double)outTileShape[_nDims - 2]));
              _gridYSpinner.addChangeListener(_gridChangeListener);
            }
            if (_nDims == 3 && _gridZSpinner != null) {
              _gridZSpinner.removeChangeListener(_gridChangeListener);
              _gridZSpinner.setValue(
                  (int)Math.ceil((double)scaledShape[0] /
                                 (double)outTileShape[0]));
              _gridZSpinner.addChangeListener(_gridChangeListener);
            }

            // Update tile shape spinners, this triggers recomputation of
            // memory consumption
            if (_shapeXSpinner != null) {
              _shapeXSpinner.removeChangeListener(
                  _shapeChangeUpdateGridAndNTilesListener);
              _shapeXSpinner.setValue(outTileShape[_nDims - 1]);
              _shapeXSpinner.addChangeListener(
                  _shapeChangeUpdateGridAndNTilesListener);
            }
            if (_shapeYSpinner != null) {
              _shapeYSpinner.removeChangeListener(
                  _shapeChangeUpdateGridAndNTilesListener);
              _shapeYSpinner.setValue(outTileShape[_nDims - 2]);
              _shapeYSpinner.addChangeListener(
                  _shapeChangeUpdateGridAndNTilesListener);
            }
            if (_nDims == 3 && _shapeZSpinner != null) {
              _shapeZSpinner.removeChangeListener(
                  _shapeChangeUpdateGridAndNTilesListener);
              _shapeZSpinner.setValue(outTileShape[0]);
              _shapeZSpinner.addChangeListener(
                  _shapeChangeUpdateGridAndNTilesListener);
            }
          }
        };

    _nTilesSpinner.addChangeListener(_nTilesChangeListener);

    // Trigger update of related tiling panels and memory display
    _nTilesChangeListener.stateChanged(new ChangeEvent(_nTilesSpinner));

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
            (int)Prefs.get("unet." + id + ".tileGridX", 5), 1,
            (int)Integer.MAX_VALUE, 1));
    panel.add(_gridXSpinner);
    panel.add(new JLabel(" y: "));
    _gridYSpinner = new JSpinner(
        new SpinnerNumberModel(
            (int)Prefs.get("unet." + id + ".tileGridY", 5), 1,
            (int)Integer.MAX_VALUE, 1));
    panel.add(_gridYSpinner);
    if (_nDims == 3) {
      panel.add(new JLabel(" z: "));
      _gridZSpinner = new JSpinner(
          new SpinnerNumberModel(
              (int)Prefs.get("unet." + id + ".tileGridZ", 5), 1,
              (int)Integer.MAX_VALUE, 1));
      panel.add(_gridZSpinner);
    }

    _gridChangeListener = new ChangeListener() {
          @Override
          public void stateChanged(ChangeEvent e) {
            if (_job instanceof SegmentationJob) {
              ImagePlus imp = ((SegmentationJob)_job).image();
              int[] scaledShape = getScaledShape(imp);
              if (scaledShape == null) return;
              int[] outTileShape = new int[_nDims];
              outTileShape[_nDims - 1] = (int)Math.ceil(
                  (double)scaledShape[_nDims - 1] /
                  ((Integer)_gridXSpinner.getValue()).doubleValue());
              outTileShape[_nDims - 2] = (int)Math.ceil(
                  (double)scaledShape[_nDims - 2] /
                  ((Integer)_gridYSpinner.getValue()).doubleValue());
              if (_nDims == 3) {
                outTileShape[0] = (int)Math.ceil(
                    (double)scaledShape[0] /
                    ((Integer)_gridZSpinner.getValue()).doubleValue());
              }
              int nTiles =
                  (Integer)_gridXSpinner.getValue() *
                  (Integer)_gridYSpinner.getValue() *
                  ((_nDims == 3) ?
                   ((Integer)_gridZSpinner.getValue()).intValue() : 1);
              for (int d = 0; d < _nDims; ++d) {
                outTileShape[d] =
                    Math.max(
                        (int)Math.ceil(
                            (double)(outTileShape[d] - _minOutTileShape[d]) /
                            (double)downsampleFactor[d]) * downsampleFactor[d] +
                        _minOutTileShape[d], _minOutTileShape[d]);
              }

              if (_shapeXSpinner != null) {
                _shapeXSpinner.removeChangeListener(
                    _shapeChangeUpdateGridAndNTilesListener);
                _shapeXSpinner.setValue(outTileShape[_nDims - 1]);
                _shapeXSpinner.addChangeListener(
                    _shapeChangeUpdateGridAndNTilesListener);
              }
              if (_shapeYSpinner != null) {
                _shapeYSpinner.removeChangeListener(
                    _shapeChangeUpdateGridAndNTilesListener);
                _shapeYSpinner.setValue(outTileShape[_nDims - 2]);
                _shapeYSpinner.addChangeListener(
                    _shapeChangeUpdateGridAndNTilesListener);
              }
              if (_nDims == 3 && _shapeZSpinner != null) {
                _shapeZSpinner.removeChangeListener(
                    _shapeChangeUpdateGridAndNTilesListener);
                _shapeZSpinner.setValue(outTileShape[0]);
                _shapeZSpinner.addChangeListener(
                    _shapeChangeUpdateGridAndNTilesListener);
              }
              if (_nTilesSpinner != null) {
                _nTilesSpinner.removeChangeListener(_nTilesChangeListener);
                _nTilesSpinner.setValue(nTiles);
                _nTilesSpinner.addChangeListener(_nTilesChangeListener);
              }
            }
          }
        };
    _gridXSpinner.addChangeListener(_gridChangeListener);
    _gridYSpinner.addChangeListener(_gridChangeListener);
    if (_nDims == 3) _gridZSpinner.addChangeListener(_gridChangeListener);

    // Trigger update of related tiling panels and memory display
    _gridChangeListener.stateChanged(new ChangeEvent(_gridXSpinner));

    _tileModePanel.add(panel, GRID);
    _tileModeSelector.addItem(GRID);
  }

  private void createMemoryCard() {
    _gpuMemSpinner = new JSpinner(
        new SpinnerNumberModel(
            (int)Prefs.get("unet." + id + ".GPUMemoryMB", 1000), 1,
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

  public void save(File outputFile) throws HDF5Exception, IOException {
    Tools.createFolder(outputFile.getParentFile());
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
    writer.float64().writeArray("/unet_param/element_size_um", elementSizeUm());
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
    if (classNames != null)
        writer.string().writeArray("/unet_param/classNames", classNames);
    if (memoryMap != null && memoryMap.length == 2)
        writer.int32().writeMatrix(
            "/unet_param/mapInputNumPxGPUMemMB", memoryMap);
    writer.close();
  }

  public void save() throws HDF5Exception, IOException {
    save(file);
  }

  // This saves the complete model plus the object state for
  // re-initialization
  public void saveSnapshot(File outputFile) throws HDF5Exception, IOException {
    IHDF5Writer writer =
        HDF5Factory.configure(outputFile).
        syncMode(IHDF5WriterConfigurator.SyncMode.SYNC_BLOCK)
        .useSimpleDataSpaceForAttributes()
        .useUTF8CharacterEncoding().overwrite().writer();

    writer.object().createGroup("/modeldef");
    if (_minOutTileShape != null) writer.int32().setArrayAttr(
        "/modeldef", "minOutTileShape", _minOutTileShape);
    writer.int32().setAttr("/modeldef", "nDims", _nDims);
    if (file != null)
        writer.string().setAttr("/modeldef", "file", file.getAbsolutePath());
    if (remoteAbsolutePath != null)
        writer.string().setAttr(
            "/modeldef", "remoteAbsolutePath", remoteAbsolutePath);
    if (modelPrototxtAbsolutePath != null)
        writer.string().setAttr(
            "/modeldef", "modelPrototxtAbsolutePath",
            modelPrototxtAbsolutePath);
    if (solverPrototxtAbsolutePath != null)
        writer.string().setAttr(
            "/modeldef", "solverPrototxtAbsolutePath",
            solverPrototxtAbsolutePath);
    if (weightFile != null)
        writer.string().setAttr("/modeldef", "weightFile", weightFile);
    writer.close();

    save(outputFile);
  }

  public void loadSnapshot(File inputFile) throws HDF5Exception {
    _load(inputFile);
    IHDF5Reader reader = HDF5Factory.configureForReading(inputFile).reader();
    try {
      _minOutTileShape =
          reader.int32().getArrayAttr("/modeldef", "minOutTileShape");
    }
    catch (HDF5Exception e) {}
    try {
      file = new File(reader.string().getAttr("/modeldef", "file"));
    }
    catch (HDF5Exception e) {}
    try {
      remoteAbsolutePath =
          reader.string().getAttr("/modeldef", "remoteAbsolutePath");
    }
    catch (HDF5Exception e) {}
    try {
      modelPrototxtAbsolutePath =
          reader.string().getAttr("/modeldef", "modelPrototxtAbsolutePath");
    }
    catch (HDF5Exception e) {}
    try {
      solverPrototxtAbsolutePath =
          reader.string().getAttr("/modeldef", "solverPrototxtAbsolutePath");
    }
    catch (HDF5Exception e) {}
    try {
      weightFile =
          reader.string().getAttr("/modeldef", "weightFile");
    }
    catch (HDF5Exception e) {}
    reader.close();
    _initGUIElements();
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
    Prefs.set("unet." + id + ".weightFile", weightFile);
    if (_job != null && _job instanceof SegmentationJob)
        Prefs.set("unet." + id + ".tilingOption",
                  (String)_tileModeSelector.getSelectedItem());

    if (((String)_tileModeSelector.getSelectedItem()).equals(NTILES)) {
      Prefs.set("unet." + id + ".nTiles", (Integer)_nTilesSpinner.getValue());
      return;
    }

    if (((String)_tileModeSelector.getSelectedItem()).equals(GRID)) {
      Prefs.set("unet." + id + ".tileGridX",
                (Integer)_gridXSpinner.getValue());
      Prefs.set("unet." + id + ".tileGridY",
                (Integer)_gridYSpinner.getValue());
      if (_nDims == 3)
          Prefs.set("unet." + id + ".tileGridZ",
                    (Integer)_gridZSpinner.getValue());
      return;
    }

    String prefsPrefix = (_job != null && _job instanceof FinetuneJob) ?
        "unet.finetuning." : "unet.segmentation.";
    if (((String)_tileModeSelector.getSelectedItem()).equals(SHAPE)) {
      Prefs.set(prefsPrefix + id + ".tileShapeX",
                (Integer)_shapeXSpinner.getValue());
      Prefs.set(prefsPrefix + id + ".tileShapeY",
                (Integer)_shapeYSpinner.getValue());
      if (_nDims == 3)
          Prefs.set(prefsPrefix + id + ".tileShapeZ",
                    (Integer)_shapeZSpinner.getValue());
      return;
    }

    if (((String)_tileModeSelector.getSelectedItem()).equals(MEMORY)) {
      Prefs.set("unet." + id + ".GPUMemoryMB",
                (Integer)_gpuMemSpinner.getValue());
      return;
    }

    throw new UnsupportedOperationException(
        "Cannot handle unknown tiling mode '" +
        (String)_tileModeSelector.getSelectedItem() + "'");
  }

  public void updateMemoryConsumptionDisplay() {
    _shapeChangeUpdateMemoryListener.stateChanged(
      new ChangeEvent(_shapeXSpinner));
  }
  
  public long computeMemoryConsumptionInTestPhase(boolean cuDNN) {
    if (_job == null || !(_job instanceof SegmentationJob ||
                          _job instanceof DetectionJob)) return -1;
    try {
      ImagePlus imp = ((SegmentationJob)_job).image();
      Caffe.NetParameter.Builder netParamBuilder =
          Caffe.NetParameter.newBuilder();
      TextFormat.getParser().merge(modelPrototxt, netParamBuilder);
      int[] inputTileShape = getInputTileShape(getTileShape());
      long[] inputBlobShape = new long[_nDims + 2];
      inputBlobShape[0] = 1;
      inputBlobShape[1] = imp.getNChannels();
      for (int d = 0; d < _nDims; ++d)
          inputBlobShape[d + 2] = inputTileShape[d];
      Net net = Net.createFromProto(
          netParamBuilder.build(), new String[] { inputBlobName },
          new long[][] { inputBlobShape }, Caffe.Phase.TEST);
      return net.memoryTotal(cuDNN);
    }
    catch (Exception e) {
      return -1;
    }
  }

  public long computeMemoryConsumptionInTrainPhase(boolean cuDNN) {
    if (_job == null || !(_job instanceof FinetuneJob)) return -1;
    try {
      FinetuneJob job = (FinetuneJob)_job;
      ImagePlus imp = null;
      if (job.trainingList().getModel().getSize() > 0)
          imp = job.trainingList().getModel().getElementAt(0);
      else if (job.validationList().getModel().getSize() > 0)
          imp = job.validationList().getModel().getElementAt(0);
      else return -1;
      Caffe.NetParameter.Builder netParamBuilder =
          Caffe.NetParameter.newBuilder();
      TextFormat.getParser().merge(modelPrototxt, netParamBuilder);
      int[] inputTileShape = getTileShape();
      long[] inputBlobShape = new long[_nDims + 2];
      inputBlobShape[0] = 1;
      inputBlobShape[1] = imp.getNChannels();
      for (int d = 0; d < _nDims; ++d)
          inputBlobShape[d + 2] = inputTileShape[d];
      Net net = Net.createFromProto(
          netParamBuilder.build(), new String[] { inputBlobName },
          new long[][] { inputBlobShape }, Caffe.Phase.TRAIN);
      return (job.validationList().getModel().getSize() > 0) ?
          net.memoryTotalWithValidation(cuDNN) :
          net.memoryTotal(cuDNN);
    }
    catch (Exception e) {
      return -1;
    }
  }

  @Override
  public String toString() {
    return name;
  }

  public String dump() {
    String res =
        "ModelDefinition {\n" +
        "  file = " + ((file != null) ? file.getAbsolutePath() : "N/A") + "\n" +
        "  remoteAbsolutePath = " +
        ((remoteAbsolutePath != null) ? remoteAbsolutePath : "N/A") + "\n" +
        "  modelPrototxtAbsolutePath = " +
        ((modelPrototxtAbsolutePath != null) ? modelPrototxtAbsolutePath :
         "N/A") + "\n" +
        "  solverPrototxtAbsolutePath = " +
        ((solverPrototxtAbsolutePath != null) ? solverPrototxtAbsolutePath :
         "N/A") + "\n" +
        "  id = " + id + "\n" +
        "  name = " + name + "\n" +
        "  description = " + description + "\n" +
        "  inputBlobName = " +
        ((inputBlobName != null) ? inputBlobName : "N/A") + "\n" +
        "  inputDatasetName = " +
        ((inputDatasetName != null) ? inputDatasetName : "N/A") + "\n" +
        "  solverPrototxt = " +
        ((solverPrototxt != null) ? solverPrototxt : "N/A") + "\n" +
        "  modelPrototxt = " +
        ((modelPrototxt != null) ? modelPrototxt : "N/A") + "\n" +
        "  padding = " + ((padding != null) ? padding : "N/A") + "\n" +
        "  normalizationType = " + normalizationType + "\n" +
        "  downsampleFactor = ";
    if (downsampleFactor != null) {
      for (int f : downsampleFactor) res += f + " ";
      res += "\n";
    }
    else res += "N/A\n";
    res += "  padInput = ";
    if (padInput != null) {
      for (int f : padInput) res += f + " ";
      res += "\n";
    }
    else res += "N/A\n";
    res += "  padOutput = ";
    if (padOutput != null) {
      for (int f : padOutput) res += f + " ";
      res += "\n";
    }
    else res += "N/A\n";
    res += "  elementSizeUm = ";
    if (elementSizeUm() != null) {
      for (double f : elementSizeUm()) res += f + " ";
      res += "\n";
    }
    else res += "N/A\n";
    res += "  memoryMap = ";
    if (memoryMap != null) {
      for (int[] m : memoryMap) {
        for (int mm : m) res += mm + " ";
        res += ";";
      }
      res += "\n";
    }
    else res += "N/A\n";
    res +=
        "  borderWeightFactor = " + borderWeightFactor + "\n" +
        "  borderWeightSigmaUm = " + borderWeightSigmaUm + "\n" +
        "  foregroundBackgroundRatio = " + foregroundBackgroundRatio + "\n" +
        "  sigma1Um = " + sigma1Um + "\n";
    res += "  classNames = ";
    if (classNames != null) {
      for (String f : classNames) res += f + " ";
      res += "\n";
    }
    else res += "N/A\n";
    res +=
        "  weightFile = " + ((weightFile != null) ? weightFile : "N/A") + "\n" +
        "}";
    return res;
  }

  private int[] getScaledShape(ImagePlus imp) {
    double[] elSizeImage = Tools.getElementSizeUm(imp);
    int[] scaledShape = new int[_nDims];

    if (_nDims == 2 && elSizeImage.length == 2) {
      scaledShape[0] = (int)Math.ceil(
          imp.getHeight() * elSizeImage[0] / elementSizeUm()[0]);
      scaledShape[1] = (int)Math.ceil(
          imp.getWidth() * elSizeImage[1] / elementSizeUm()[1]);
      return scaledShape;
    }

    if (_nDims == 2 && elSizeImage.length == 3) {
      scaledShape[0] = (int)Math.ceil(
          imp.getHeight() * elSizeImage[1] / elementSizeUm()[0]);
      scaledShape[1] = (int)Math.ceil(
          imp.getWidth() * elSizeImage[2] / elementSizeUm()[1]);
      return scaledShape;
    }

    if (_nDims == 3 && elSizeImage.length == 2) {
      scaledShape[0] = 1;
      scaledShape[1] = (int)Math.ceil(
          imp.getHeight() * elSizeImage[0] / elementSizeUm()[1]);
      scaledShape[2] = (int)Math.ceil(
          imp.getWidth() * elSizeImage[1] / elementSizeUm()[2]);
      return scaledShape;
    }

    if (_nDims == 3 && elSizeImage.length == 3) {
      scaledShape[0] = (int)Math.ceil(
          imp.getNSlices() * elSizeImage[0] / elementSizeUm()[0]);
      scaledShape[1] = (int)Math.ceil(
          imp.getHeight() * elSizeImage[1] / elementSizeUm()[1]);
      scaledShape[2] = (int)Math.ceil(
          imp.getWidth() * elSizeImage[2] / elementSizeUm()[2]);
      return scaledShape;
    }

    return null;
  }

};
