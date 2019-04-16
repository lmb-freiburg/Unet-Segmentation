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
import java.util.Map;

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

// All arrays involving dimensions are ordered [z],y,x but shown in
// oppsite order in the GUI!
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
  public int normalizationType = 1;
  public int[] downsampleFactor = null;
  public int[] padInput = null;
  public int[] padOutput = null;
  public int[][] memoryMap = null;
  public int diskRadiusPx = 2;
  public float borderWeightFactor =
      (float)Prefs.get("unet.borderWeightFactor", 50.0f);
  public float borderWeightSigmaPx =
      (float)Prefs.get("unet.borderWeightSigmaPx", 6.0f);
  public float foregroundBackgroundRatio =
      (float)Prefs.get("unet.foregroundBackgroundRatio", 0.1f);
  public float sigma1Px = (float)Prefs.get("unet.sigma1Px", 10.0f);
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
  private final ChangeListener _nTilesChangeListener = new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
              if (!(_job instanceof SegmentationJob) ||
                  (_gridSpinners == null && _shapeSpinners == null)) return;
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
              for (int d = 0; d < _nDims; ++d) {
                if (_gridSpinners != null && d < _gridSpinners.length &&
                    _gridSpinners[d] != null) {
                  _gridSpinners[d].removeChangeListener(_gridChangeListener);
                  _gridSpinners[d].setValue(
                      (int)Math.ceil((double)scaledShape[d] /
                                     (double)outTileShape[d]));
                  _gridSpinners[d].addChangeListener(_gridChangeListener);
                }
              }

              // Update tile shape spinners, this triggers recomputation of
              // memory consumption
              for (int d = 0; d < _nDims; ++d) {
                if (_shapeSpinners != null && d < _shapeSpinners.length &&
                    _shapeSpinners[d] != null) {
                  _shapeSpinners[d].removeChangeListener(
                      _shapeChangeUpdateGridAndNTilesListener);
                  _shapeSpinners[d].setValue(outTileShape[d]);
                  _shapeSpinners[d].addChangeListener(
                      _shapeChangeUpdateGridAndNTilesListener);
                }
              }
            }
          };

  private JSpinner[] _shapeSpinners = null;
  private final ChangeListener _shapeChangeUpdateGridAndNTilesListener =
      new ChangeListener() {
        @Override
        public void stateChanged(ChangeEvent e) {
          if (!(_job instanceof SegmentationJob) ||
              (_gridSpinners == null && _nTilesSpinner == null)) return;
          ImagePlus imp = ((SegmentationJob)_job).image();
          int[] scaledShape = getScaledShape(imp);
          int[] tileShape = getTileShape();
          if (scaledShape == null) return;
          int nTiles = 1;
          for (int d = 0; d < _nDims; ++d) {
            int grid = (int)Math.ceil(
                (double)scaledShape[d] / (double)tileShape[d]);
            nTiles *= grid;
            if (_gridSpinners != null && d < _gridSpinners.length &&
                _gridSpinners[d] != null) {
              _gridSpinners[d].removeChangeListener(_gridChangeListener);
              _gridSpinners[d].setValue(grid);
              _gridSpinners[d].addChangeListener(_gridChangeListener);
            }
          }
          if (_nTilesSpinner != null) {
            _nTilesSpinner.removeChangeListener(_nTilesChangeListener);
            _nTilesSpinner.setValue(nTiles);
            _nTilesSpinner.addChangeListener(_nTilesChangeListener);
          }
        }
      };

  private final ChangeListener _shapeChangeUpdateMemoryListener =
      new ChangeListener() {
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

  private JSpinner[] _gridSpinners = null;
  private final ChangeListener _gridChangeListener = new ChangeListener() {
          @Override
          public void stateChanged(ChangeEvent e) {
            if (_job instanceof SegmentationJob) {
              ImagePlus imp = ((SegmentationJob)_job).image();
              int[] scaledShape = getScaledShape(imp);
              if (scaledShape == null) return;
              int[] outTileShape = new int[_nDims];
              int nTiles = 1;
              for (int d = 0; d < _nDims; ++d) {
                outTileShape[d] = Math.max(
                    (int)Math.ceil(
                        (double)(
                            ((int)Math.ceil(
                                (double)scaledShape[d] /
                                ((Integer)_gridSpinners[d].getValue())
                                .doubleValue())) - _minOutTileShape[d]) /
                        (double)downsampleFactor[d]) * downsampleFactor[d] +
                    _minOutTileShape[d], _minOutTileShape[d]);
                if (_shapeSpinners[d] != null) {
                  _shapeSpinners[d].removeChangeListener(
                      _shapeChangeUpdateGridAndNTilesListener);
                  _shapeSpinners[d].setValue(outTileShape[d]);
                  _shapeSpinners[d].addChangeListener(
                      _shapeChangeUpdateGridAndNTilesListener);
                }
                nTiles *= (Integer)_gridSpinners[d].getValue();
              }
              if (_nTilesSpinner != null) {
                _nTilesSpinner.removeChangeListener(_nTilesChangeListener);
                _nTilesSpinner.setValue(nTiles);
                _nTilesSpinner.addChangeListener(_nTilesChangeListener);
              }
            }
          }
        };

  private JSpinner _gpuMemSpinner = null;

  private final JPanel _elementSizeUmPanel = new JPanel(
      new FlowLayout(FlowLayout.LEFT, 0, 0));
  private final JSpinner[] _elSizeSpinners = new JSpinner[] {
      new JSpinner(
          new SpinnerNumberModel(1.0, 0.0000001, 1000000.0, 0.01)),
      new JSpinner(
          new SpinnerNumberModel(1.0, 0.0000001, 1000000.0, 0.01)),
      new JSpinner(
          new SpinnerNumberModel(1.0, 0.0000001, 1000000.0, 0.01)) };

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
    for (int d = 0; d < 3; ++d)
        ((JSpinner.NumberEditor)_elSizeSpinners[d].getEditor())
            .getFormat().applyPattern("######0.0######");
  }

  public ModelDefinition(Job job, Map<String,String> parameters)
      throws HDF5Exception {

    this(job);

    if (parameters == null || !parameters.containsKey("modelFilename")) return;

    file = new File(parameters.get("modelFilename"));
    load();

    if (parameters.containsKey(SHAPE)) {
      _tileModeSelector.setSelectedItem(SHAPE);
      String[] st = parameters.get(SHAPE).split("x");
      for (int d = 0; d < _nDims && d < st.length; ++d)
          _shapeSpinners[_nDims - d - 1].setValue(Integer.valueOf(st[d]));
    }

    if (_nDims == 2) {
      if (parameters.containsKey(NTILES)) {
        _tileModeSelector.setSelectedItem(NTILES);
        _nTilesSpinner.setValue(Integer.valueOf(parameters.get(NTILES)));
      }

      if (parameters.containsKey(GRID)) {
        _tileModeSelector.setSelectedItem(GRID);
        String[] st = parameters.get(GRID).split("x");
        for (int d = 0; d < _nDims && d < st.length; ++d)
            _gridSpinners[_nDims - d - 1].setValue(Integer.valueOf(st[d]));
      }

      if (parameters.containsKey(MEMORY) && _gpuMemSpinner != null) {
        _tileModeSelector.setSelectedItem(MEMORY);
        _gpuMemSpinner.setValue(Integer.valueOf(parameters.get(MEMORY)));
      }
    }
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
    if (_tileModeSelector.getSelectedItem().equals(SHAPE)) {
      _shapeChangeUpdateGridAndNTilesListener.stateChanged(
          new ChangeEvent(_shapeSpinners[0]));
      _shapeChangeUpdateMemoryListener.stateChanged(
          new ChangeEvent(_shapeSpinners[0]));
    }
    if (_tileModeSelector.getSelectedItem().equals(GRID))
        _gridChangeListener.stateChanged(new ChangeEvent(_gridSpinners[0]));
    if (_tileModeSelector.getSelectedItem().equals(NTILES))
        _nTilesChangeListener.stateChanged(new ChangeEvent(_nTilesSpinner));
  }

  public void setElementSizeUm(String elSizeString)
      throws NumberFormatException {
    String[] elSizeTokens = elSizeString.split("x");
    double[] elSize = new double[elSizeTokens.length];
    for (int d = 0; d < elSize.length; ++d)
        elSize[d] = Double.parseDouble(elSizeTokens[d]);
    this.setElementSizeUm(elSize);
  }

  public String getElementSizeUmString() {
    double[] elSize = this.elementSizeUm();
    String elSizeString = new Double(elSize[0]).toString();
    for (int d = 1; d < elSize.length; ++d) elSizeString += "x" + elSize[d];
    return elSizeString;
  }

  public void setElementSizeUm(double[] elSize) {
    if (elSize == null || elSize.length < 2 || elSize.length > 3) return;

    // Model dimension is not set ==> Panel must be initialized
    if (!isValid()) {
      _nDims = elSize.length;
      _elementSizeUmPanel.removeAll();
      _elementSizeUmPanel.add(new JLabel(" x: "));
      _elementSizeUmPanel.add(_elSizeSpinners[_nDims - 1]);
      _elementSizeUmPanel.add(new JLabel(" y: "));
      _elementSizeUmPanel.add(_elSizeSpinners[_nDims - 2]);
      if (_nDims == 3) {
        _elementSizeUmPanel.add(new JLabel(" z: "));
        _elementSizeUmPanel.add(_elSizeSpinners[_nDims - 3]);
      }
    }

    // Case 1: _nDims and given element size dimension match ==> update
    if (elSize.length == _nDims) {
      for (int d = 0; d < _nDims; ++d)
          _elSizeSpinners[d].setValue((double)elSize[d]);
      return;
    }

    // Case 2: _nDims == 2 && elSize.length == 3 ==> update spatial dimensions
    if (elSize.length == 3 && _nDims == 2) {
      for (int d = 0; d < _nDims; ++d)
          _elSizeSpinners[d].setValue((double)elSize[d + 1]);
      return;
    }

    // Case 3: _nDims == 3 && elSize.length == 2 ==> update spatial dimensions
    if (elSize.length == 2 && _nDims == 3) {
      for (int d = 0; d < elSize.length; ++d)
          _elSizeSpinners[d + 1].setValue((double)elSize[d]);
      return;
    }
  }

  public double[] elementSizeUm() {
    if (!isValid()) return null;
    double[] res = new double[_nDims];
    for (int d = 0; d < _nDims; ++d)
        res[d] = ((Double)_elSizeSpinners[d].getValue()).doubleValue();
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
    dup.diskRadiusPx = diskRadiusPx;
    dup.borderWeightFactor = borderWeightFactor;
    dup.borderWeightSigmaPx = borderWeightSigmaPx;
    dup.foregroundBackgroundRatio = foregroundBackgroundRatio;
    dup.sigma1Px = sigma1Px;
    if (classNames != null) {
      dup.classNames = new String[classNames.length];
      for (int i = 0; i < classNames.length; i++)
          dup.classNames[i] = classNames[i];
    }
    dup.weightFile = weightFile;

    // This creates and initializes all required GUI elements
    dup._initGUIElements();

    if (_nTilesSpinner != null)
        dup._nTilesSpinner.setValue((Integer)_nTilesSpinner.getValue());
    if (_gpuMemSpinner != null)
        dup._gpuMemSpinner.setValue((Integer)_gpuMemSpinner.getValue());
    for (int d = 0; d < _nDims; ++d) {
      if (_gridSpinners != null && d < _gridSpinners.length &&
          _gridSpinners[d] != null)
          dup._gridSpinners[d].setValue((Integer)_gridSpinners[d].getValue());
      if (_shapeSpinners != null && d < _shapeSpinners.length &&
          _shapeSpinners[d] != null)
          dup._shapeSpinners[d].setValue((Integer)_shapeSpinners[d].getValue());
    }
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
    catch (HDF5Exception e) {
      inputDatasetName = "data";
    }
    solverPrototxt = reader.string().read("/solver_prototxt");
    modelPrototxt = reader.string().read("/model_prototxt");
    padding = reader.string().read("/unet_param/padding");
    normalizationType = reader.int32().read("/unet_param/normalization_type");
    _nDims = -1;
    setElementSizeUm(reader.float64().readArray("/unet_param/element_size_um"));
    downsampleFactor = reader.int32().readArray("/unet_param/downsampleFactor");
    padInput = reader.int32().readArray("/unet_param/padInput");
    padOutput = reader.int32().readArray("/unet_param/padOutput");
    try {
      borderWeightFactor = reader.float32().read(
          "/unet_param/pixelwise_loss_weights/borderWeightFactor");
      borderWeightSigmaPx = reader.float32().read(
          "/unet_param/pixelwise_loss_weights/borderWeightSigmaPx");
      foregroundBackgroundRatio = reader.float32().read(
          "/unet_param/pixelwise_loss_weights/foregroundBackgroundRatio");
      sigma1Px = reader.float32().read(
          "/unet_param/pixelwise_loss_weights/sigma1Px");
    }
    catch (HDF5Exception e) {
      borderWeightFactor = (float)Prefs.get("unet.borderWeightFactor", 50.0f);
      borderWeightSigmaPx = (float)Prefs.get("unet.borderWeightSigmaPx", 6.0f);
      foregroundBackgroundRatio =
          (float)Prefs.get("unet.foregroundBackgroundRatio", 0.1f);
      sigma1Px = (float)Prefs.get("unet.sigma1Px", 10.0f);
    }
    try {
      diskRadiusPx = reader.int32().read("/unet_param/diskRadiusPx");
    }
    catch (HDF5Exception e) {
      diskRadiusPx = 2;
    }

    try {
      classNames = reader.string().readArray("/unet_param/classNames");
    }
    catch (HDF5Exception e) {
      classNames = null;
    }

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

  public int[] getMinimumOutputShape() {
    if (_minOutTileShape == null || _minOutTileShape.length != _nDims) {
      // Compute minimum output tile shape
      _minOutTileShape = new int[_nDims];
      for (int d = 0; d < _nDims; d++) {
        _minOutTileShape[d] = padOutput[d];
        while (_minOutTileShape[d] < 0)
            _minOutTileShape[d] += downsampleFactor[d];
      }
    }
    return _minOutTileShape;
  }

  public int[] getMinimumInputShape() {
    return getInputTileShape(getMinimumOutputShape());
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

  private JSpinner getShapeSpinner(int dim, String prefsPrefix) {
    if (_shapeSpinners != null && _shapeSpinners.length == _nDims &&
        _shapeSpinners[dim] != null) return _shapeSpinners[dim];

    if (_shapeSpinners == null || _shapeSpinners.length != _nDims)
        _shapeSpinners = new JSpinner[_nDims];

    int dsFactor = downsampleFactor[dim];
    final int minValue =
        (_job != null && _job instanceof SegmentationJob) ?
        _minOutTileShape[dim] : getInputTileShape(_minOutTileShape)[dim];
    _shapeSpinners[dim] = new JSpinner(
        new SpinnerNumberModel(
            minValue, minValue, (int)Integer.MAX_VALUE, dsFactor));
    _shapeSpinners[dim].addChangeListener(new ChangeListener() {
          @Override
          public void stateChanged(ChangeEvent e) {
            int value = (Integer)_shapeSpinners[dim].getValue();
            int nextValidSize = Math.max(
                (int)Math.floor((value - minValue) / dsFactor) *
                dsFactor + minValue, minValue);
            if (nextValidSize == value) return;
            _shapeSpinners[dim].setValue(nextValidSize);
          }
        });
    _shapeSpinners[dim].setValue(
        (int)Prefs.get(
            prefsPrefix + id + ".tileShape_" + String.valueOf(dim),
            minValue + 10 * dsFactor));
    return _shapeSpinners[dim];
  }

  private void createTileShapeCard() {
    JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    panel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
    ((FlowLayout)panel.getLayout()).setAlignOnBaseline(true);

    String prefsPrefix = (_job != null && _job instanceof FinetuneJob) ?
        "unet.finetuning." : "unet.segmentation.";

    panel.add(new JLabel(" x: "));
    panel.add(getShapeSpinner(_nDims - 1, prefsPrefix));
    panel.add(new JLabel(" y: "));
    panel.add(getShapeSpinner(_nDims - 2, prefsPrefix));
    if (_nDims == 3) {
      panel.add(new JLabel(" z: "));
      panel.add(getShapeSpinner(_nDims - 3, prefsPrefix));
    }

    for (int d = 0; d < _nDims; ++d) {
      if (!Arrays.asList(_shapeSpinners[d].getChangeListeners()).contains(
              _shapeChangeUpdateGridAndNTilesListener))
          _shapeSpinners[d].addChangeListener(
              _shapeChangeUpdateGridAndNTilesListener);
      if (!Arrays.asList(_shapeSpinners[d].getChangeListeners()).contains(
              _shapeChangeUpdateMemoryListener))
          _shapeSpinners[d].addChangeListener(_shapeChangeUpdateMemoryListener);
    }

    _tileModePanel.add(panel, SHAPE);
    _tileModeSelector.addItem(SHAPE);
  }

  private void createNTilesCard() {
    if (_nTilesSpinner == null) {
      _nTilesSpinner = new JSpinner(
          new SpinnerNumberModel(
              (int)Prefs.get("unet." + id + ".nTiles", 1), 1,
              (int)Integer.MAX_VALUE, 1));
      _nTilesSpinner.addChangeListener(_nTilesChangeListener);
    }
    _tileModePanel.add(_nTilesSpinner, NTILES);
    _tileModeSelector.addItem(NTILES);
  }

  private JSpinner getGridSpinner(int dim) {
    if (_gridSpinners != null && _gridSpinners.length == _nDims &&
        _gridSpinners[dim] != null) return _gridSpinners[dim];

    if (_gridSpinners == null || _gridSpinners.length != _nDims)
        _gridSpinners = new JSpinner[_nDims];

    _gridSpinners[dim] = new JSpinner(
        new SpinnerNumberModel(
            (int)Prefs.get(
                "unet." + id + ".tileGrid_" + String.valueOf(dim), 5), 1,
            (int)Integer.MAX_VALUE, 1));
    return _gridSpinners[dim];
  }

  private void createTileGridCard() {
    JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    panel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
    ((FlowLayout)panel.getLayout()).setAlignOnBaseline(true);

    if (_gridSpinners == null || _gridSpinners.length != _nDims)
        _gridSpinners = new JSpinner[_nDims];

    panel.add(new JLabel(" x: "));
    panel.add(getGridSpinner(_nDims - 1));
    panel.add(new JLabel(" y: "));
    panel.add(getGridSpinner(_nDims - 2));
    if (_nDims == 3) {
      panel.add(new JLabel(" z: "));
      panel.add(getGridSpinner(_nDims - 3));
    }

    for (int d = 0; d < _nDims; ++d)
        if (!Arrays.asList(_gridSpinners[d].getChangeListeners()).contains(
                _gridChangeListener))
            _gridSpinners[d].addChangeListener(_gridChangeListener);

    _tileModePanel.add(panel, GRID);
    _tileModeSelector.addItem(GRID);
  }

  private void createMemoryCard() {
    if (_gpuMemSpinner == null)
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
          "/unet_param/pixelwise_loss_weights/borderWeightSigmaPx",
          borderWeightSigmaPx);
    writer.float32().write(
        "/unet_param/pixelwise_loss_weights/foregroundBackgroundRatio",
        foregroundBackgroundRatio);
    writer.float32().write(
        "/unet_param/pixelwise_loss_weights/sigma1Px", sigma1Px);
    writer.int32().write("/unet_param/diskRadiusPx", diskRadiusPx);
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

  public String getMacroParameterString() {
    if (file == null) return "";
    String res = "modelFilename=" + file.getAbsolutePath().replace("\\", "/");
    if (((String)_tileModeSelector.getSelectedItem()).equals(NTILES))
        res += "," + NTILES + "=" + (Integer)_nTilesSpinner.getValue();
    if (((String)_tileModeSelector.getSelectedItem()).equals(GRID))
        res += "," + GRID + "=" +
            (Integer)_gridSpinners[_nDims - 1].getValue() +
            "x" + (Integer)_gridSpinners[_nDims - 2].getValue() +
            ((_nDims == 3) ?
             ("x" + (Integer)_gridSpinners[_nDims - 3].getValue()) : "");
    if (((String)_tileModeSelector.getSelectedItem()).equals(SHAPE))
        res += "," + SHAPE + "=" +
            (Integer)_shapeSpinners[_nDims - 1].getValue() + "x" +
            (Integer)_shapeSpinners[_nDims - 2].getValue() +
            ((_nDims == 3) ?
             ("x" + (Integer)_shapeSpinners[_nDims - 3].getValue()) : "");
    if (((String)_tileModeSelector.getSelectedItem()).equals(MEMORY))
        res += "," + MEMORY + "=" + (Integer)_gpuMemSpinner.getValue();
    return res;
  }

  public String getCaffeTilingParameter() {
    if (((String)_tileModeSelector.getSelectedItem()).equals(NTILES))
        return "-n_tiles " + (Integer)_nTilesSpinner.getValue();
    if (((String)_tileModeSelector.getSelectedItem()).equals(GRID)) {
      String res = "-n_tiles ";
      for (int d = 0; d < _nDims - 1; ++d)
          res += (Integer)_gridSpinners[d].getValue() + "x";
      res += (Integer)_gridSpinners[_nDims - 1].getValue();
      return res;
    }
    if (((String)_tileModeSelector.getSelectedItem()).equals(SHAPE)) {
      String res = "-tile_size ";
      for (int d = 0; d < _nDims - 1; ++d)
          res += (Integer)_shapeSpinners[d].getValue() + "x";
      res += (Integer)_shapeSpinners[_nDims - 1].getValue();
      return res;
    }
    if (((String)_tileModeSelector.getSelectedItem()).equals(MEMORY))
        return "-gpu_mem_available_MB " +
            (Integer)_gpuMemSpinner.getValue();
    return "";
  }

  public String getProtobufTileShapeString() {
    return ((_nDims == 3) ?
            ("nz: " + (Integer)_shapeSpinners[0].getValue()) + " " : "") +
        "ny: " + (Integer)_shapeSpinners[_nDims - 2].getValue() +
        " nx: " + (Integer)_shapeSpinners[_nDims - 1].getValue();
  }

  public int[] getTileShape() {
    int[] res = new int[_nDims];
    for (int d = 0; d < _nDims; ++d)
        res[d] = (Integer)_shapeSpinners[d].getValue();
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
      Prefs.set("unet." + id + ".tileGrid_" + String.valueOf(_nDims - 1),
                (Integer)_gridSpinners[_nDims - 1].getValue());
      Prefs.set("unet." + id + ".tileGrid_" + String.valueOf(_nDims - 2),
                (Integer)_gridSpinners[_nDims - 2].getValue());
      if (_nDims == 3)
          Prefs.set("unet." + id + ".tileGrid_0",
                    (Integer)_gridSpinners[0].getValue());
      return;
    }

    String prefsPrefix = (_job != null && _job instanceof FinetuneJob) ?
        "unet.finetuning." : "unet.segmentation.";
    if (((String)_tileModeSelector.getSelectedItem()).equals(SHAPE)) {
      Prefs.set(prefsPrefix + id + ".tileShape_" + String.valueOf(_nDims - 1),
                (Integer)_shapeSpinners[_nDims - 1].getValue());
      Prefs.set(prefsPrefix + id + ".tileShape_" + String.valueOf(_nDims - 2),
                (Integer)_shapeSpinners[_nDims - 2].getValue());
      if (_nDims == 3)
          Prefs.set(prefsPrefix + id + ".tileShape_0",
                    (Integer)_shapeSpinners[0].getValue());
      return;
    }

    if (((String)_tileModeSelector.getSelectedItem()).equals(MEMORY)) {
      Prefs.set("unet." + id + ".GPUMemoryMB",
                (Integer)_gpuMemSpinner.getValue());
      return;
    }
  }

  public void updateMemoryConsumptionDisplay() {
    if (_shapeSpinners != null && _shapeSpinners.length > 0 &&
        _shapeSpinners[0] != null)
        _shapeChangeUpdateMemoryListener.stateChanged(
            new ChangeEvent(_shapeSpinners[0]));
  }

  public long computeMemoryConsumptionInTestPhase(boolean cuDNN) {
    if (_job == null || !(_job instanceof SegmentationJob)) return -1;
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
        "  diskRadiusPx = " + diskRadiusPx + "\n" +
        "  borderWeightFactor = " + borderWeightFactor + "\n" +
        "  borderWeightSigmaPx = " + borderWeightSigmaPx + "\n" +
        "  foregroundBackgroundRatio = " + foregroundBackgroundRatio + "\n" +
        "  sigma1Px = " + sigma1Px + "\n";
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
