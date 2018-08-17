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

import caffe.Caffe;

import java.util.Vector;
import java.util.UUID;
import java.util.Arrays;

/**
 * The Net class simulates parts of the caffe::Net class to compute the
 * memory consumption of a neural network in caffe.
 *
 * @author Thorsten Falk
 * @version 1.0
 * @since 1.0
 */
public class Net {

/**
 * Creates a new uninitialized network.
 * <p>
 * The phase parameter decides whether the memory consumption of the network
 * is estimated for the forward pass only
 * (<code>phase == Caffe.Phase.TEST</code>) or the complete forward-backward
 * cycle (<code>phase == Caffe.Phase.TRAIN</code>).
 *
 * @param phase The network phase to compute the memory consumption for
 */
  public Net(Caffe.Phase phase) {
    _phase = phase;
  }

/**
 * Factory method creating a new network from the given compiled protobuf
 * parameters.
 * <p>
 * It will first create a new uninitialized network and then subsequently
 * add layers from the netParam structure for which all input blobs are
 * available. Parts not downstream connected to the generated input blobs
 * will be ignored. Layers that are not used in the requested phase will be
 * ignored. Finally the network structure will be searched for data flow
 * branches to add corresponding split layers.
 * <p>
 * The phase parameter decides whether the memory consumption of the network
 * is estimated for the forward pass only
 * (<code>phase == Caffe.Phase.TEST</code>) or the complete forward-backward
 * cycle (<code>phase == Caffe.Phase.TRAIN</code>).
 *
 * @param netParam The compiled protobuf network architecture definition
 * @param inputBlobNames If this array is not null, a <code>DataLayer</code>
 *   is generated producing output blobs with the given names and shapes. If
 *   the network contains an an HDF5DataLayer, pass null here to use that
 *   Layer as data source.
 * @param inputBlobShapes The given blob shapes are used to set the output
 *   blob shapes of the generated DataLayer. You can either explicitly specify
 *   input blobs using the inputBlobNames parameter, or the given shapes
 *   are used whenever the parser encounters an HDF5DataLayer to reshape its
 *   outputs accordingly.
 * @param phase The network phase to compute the memory consumption for
 *
 * @return the created <code>Net</code> object
 *
 * @throws NotImplementedException if a layer type is encountered, that has
 *   no corresponding implementation
 * @throws BlobException if during network construction an invalid blob shape
 *   is generated, for example zero size blobs.
 *
 * @see de.unifreiburg.unet.DataLayer
 */
  public static Net createFromProto(
      Caffe.NetParameter netParam, String[] inputBlobNames,
      long[][] inputBlobShapes, Caffe.Phase phase)
      throws NotImplementedException, BlobException {

    Net net = new Net(phase);

    Caffe.InputParameter.Builder ib = Caffe.InputParameter.newBuilder();
    for (long[] shape : inputBlobShapes) {
      Caffe.BlobShape.Builder bb = Caffe.BlobShape.newBuilder();
      for (long extent : shape) bb.addDim(extent);
      ib.addShape(bb);
    }

    if (inputBlobNames != null) {
      Caffe.LayerParameter.Builder lb = Caffe.LayerParameter.newBuilder();
      lb.setType("Input");
      lb.setName(UUID.randomUUID().toString());
      lb.setInputParam(ib);
      for (String blobName : inputBlobNames) lb.addTop(blobName);
      net.addLayer(new DataLayer(lb.build(), net));
    }

    boolean allConnected = false;
    while (!allConnected) {

      allConnected = true;

      for (Caffe.LayerParameter l : netParam.getLayerList()) {

        if (net.findLayer(l.getName()) != null) continue;

        boolean phaseIncluded = true;
        for (Caffe.NetStateRule rule : l.getExcludeList()) {
          if (rule.hasPhase() && rule.getPhase().equals(phase)) {
            phaseIncluded = false;
            break;
          }
        }
        if (!phaseIncluded) continue;
        for (Caffe.NetStateRule rule : l.getIncludeList())
        {
          if (rule.hasPhase() && !rule.getPhase().equals(phase))
              phaseIncluded = false;
          if (rule.hasPhase() && rule.getPhase().equals(phase)) {
            phaseIncluded = true;
            break;
          }
        }
        if (!phaseIncluded) continue;

        if (l.getType().equals("HDF5Data")) {
          if (l.getTopCount() > inputBlobShapes.length) {
            for (int i = inputBlobShapes.length; i < l.getTopCount(); ++i)
                ib.addShape(ib.getShape(ib.getShapeCount() - 1));
          }
          Caffe.LayerParameter.Builder lb = Caffe.LayerParameter.newBuilder();
          lb.setType("Input");
          lb.setName(l.getName());
          lb.setInputParam(ib);
          for (String blobName : l.getTopList()) lb.addTop(blobName);
          net.addLayer(new DataLayer(lb.build(), net));
          allConnected = false;
          continue;
        }

        CaffeBlob[] in = null;
        if (l.getBottomCount() > 0) {
          in = new CaffeBlob[l.getBottomCount()];
          int blobIdx = 0;
          for (; blobIdx < l.getBottomCount(); ++blobIdx) {
            in[blobIdx] = net.findBlob(l.getBottom(blobIdx));
            if (in[blobIdx] == null) break;
          }
          if (blobIdx < l.getBottomCount()) continue;
        }

        NetworkLayer layer = NetworkLayer.createFromProto(l, net, in);
        if (layer instanceof CreateDeformationLayer) {
          if (inputBlobShapes != null && inputBlobShapes.length > 0) {
            layer.outputBlobs()[0].shape()[1] = inputBlobShapes[0][2];
            layer.outputBlobs()[0].shape()[2] = inputBlobShapes[0][3];
            if (inputBlobShapes[0].length == 5)
                layer.outputBlobs()[0].shape()[3] = inputBlobShapes[0][4];
          }
        }
        net.addLayer(layer);
        allConnected = false;
      }
    }

    // Add necessary split layers
    Vector<NetworkLayer> splitLayers = new Vector<NetworkLayer>();
    Vector<CaffeBlob> splitBlobs = new Vector<CaffeBlob>();
    for (NetworkLayer l : net.layers()) {
      if (l.outputBlobs() == null) continue;
      for (int i = 0; i < l.outputBlobs().length; ++i) {
        CaffeBlob thisOutput = l.outputBlobs()[i];
        if (splitBlobs.contains(thisOutput)) continue;
        int nConsumers = 0;
        for (NetworkLayer l2 : net.layers()) {
          if (l != l2 && l2.inputBlobs() != null &&
              Arrays.asList(l2.inputBlobs()).contains(thisOutput) &&
              (l2.outputBlobs() == null ||
               !Arrays.asList(l2.outputBlobs()).contains(thisOutput))) {
            nConsumers++;
          }
        }
        if (nConsumers > 1)
        {
          Caffe.LayerParameter.Builder lb =
              Caffe.LayerParameter.newBuilder();
          lb.setType("Split");
          String name = thisOutput.name() + "_" + l.name() + "_" + i + "_split";
          lb.setName(name);
          for (int c = 0; c < nConsumers; ++c) lb.addTop(name + "_" + c);
          splitLayers.add(
              new SplitLayer(lb.build(), net, new CaffeBlob[] { thisOutput }));
          splitBlobs.add(thisOutput);
        }
      }
    }
    for (NetworkLayer layer : splitLayers) net.addLayer(layer, true);

    return net;
  }

/**
 * Add a network layer to this neural network. Input blobs to the given layer
 * must be already contained in this network's blob list. Output blobs will
 * be added to the blob list of this network, if not yet existing.
 *
 * @param layer the prepared network layer to add
 */
  public void addLayer(NetworkLayer layer) {
    addLayer(layer, false);
  }

/**
 * Add a network layer to this neural network. Input blobs to the given layer
 * must be already contained in this network's blob list. Output blobs will
 * be added to the blob list of this network, if not yet existing.
 *
 * @param layer the prepared network layer to add
 * @param isConsumed if passing true, the output blobs will be treated as if
 *   another layer is consuming them, and they will not be added to the list
 *   of output blobs of this network.
 */
  public void addLayer(NetworkLayer layer, boolean isConsumed) {
    _layers.add(layer);
    if (layer.inputBlobs() != null)
        for (CaffeBlob blob : layer.inputBlobs())
            if (_outputBlobs.contains(blob)) _outputBlobs.remove(blob);
    if (layer.outputBlobs() != null)
        for (CaffeBlob blob : layer.outputBlobs()) {
          if (!_outputBlobs.contains(blob) && !isConsumed)
              _outputBlobs.add(blob);
          if (!_blobs.contains(blob)) _blobs.add(blob);
        }
  }

/**
 * Get the list of layers this network contains
 *
 * @return The contained network layers
 */
  public Vector<NetworkLayer> layers() {
    return _layers;
  }

/**
 * Find a layer in the list of network layers by name.
 *
 * @param name the name of the layer
 * @return the first network layer found with matching name or null if no
 *   layer exists with name matching the given name
 */
  public NetworkLayer findLayer(String name) {
    for (NetworkLayer layer : _layers)
        if (layer.name().equals(name)) return layer;
    return null;
  }

/**
 * Find a <code>CaffeBlob</code> in the list of blobs used in the network
 * by name.
 *
 * @param name the name of the blob
 * @return the first blob found with matching name or null if no
 *   blob exists with name matching the given name
 */
  public CaffeBlob findBlob(String name) {
    for (CaffeBlob blob : _blobs) if (blob.name().equals(name)) return blob;
    return null;
  }

/**
 * Get the network phase the memory consumption is computed for. This method
 * is mainly used to setup the layers belonging to the network accordingly.
 *
 * @return The network phase to simulate
 */
  public Caffe.Phase phase() {
    return _phase;
  }

/**
 * Get a string representation of this network. The output is simply
 * dumping all contained network layers. Additionally a list of all blobs,
 * that are not consumed by any layer is output.
 *
 * @return A string representation of the network
 */
  @Override
  public String toString() {
    String res = "Net (phase = " +
        (_phase.equals(Caffe.Phase.TRAIN) ? "TRAIN" : "TEST")+ ") { \n";
    for (NetworkLayer layer : _layers) res += "  " + layer + "\n";
    res += "}";
    return res;
  }

/**
 * Get all blobs that are not consumed by any layer of the network.
 *
 * @return a reference to the list of output blobs produced by the network
 */
  public CaffeBlob[] outputBlobs() {
    return _outputBlobs.toArray(new CaffeBlob[_outputBlobs.size()]);
  }

/**
 * Get the memory required for learnable parameters of the network.
 *
 * @return The memory used by learnable parameters of the network in bytes
 */
  public long memoryParameters() {
    long mem = 0;
    for (NetworkLayer layer : _layers) mem += layer.memoryParameters();
    return mem;
  }

/**
 * Get the memory overhead required by the solver. Adam requires three
 * copies of the parameters.
 *
 * @return The memory overhead of the solver in bytes
 */
  public long memorySolver() {
    return (_phase.equals(Caffe.Phase.TRAIN)) ?
        3 * memoryParameters() : 0;
  }

/**
 * Get the memory overhead required for internal data structures.
 *
 * @return The memory overhead for management structures in bytes
 */
  public long memoryOther() {
    long mem = 0;
    for (NetworkLayer layer : _layers) mem += layer.memoryOther();
    return mem;
  }

/**
 * Get the memory overhead for fast convolution operations using either cuDNN
 * or matrix multiplications.
 *
 * @param cuDNN Pass true if you want to estimate the memory consumption with
 *   cuDNN enabled, false otherwise
 * @return The memory overhead in bytes
 */
  public long memoryOverhead(boolean cuDNN) {
    long mem = 0;
    for (NetworkLayer layer : _layers) mem += layer.memoryOverhead(cuDNN);
    return mem;
  }

/**
 * Get the memory required to store the blobs for the forward pass.
 *
 * @return The memory required for input, output and intermediate data in bytes
 */
  public long memoryBlobsForward() {
    long mem = 0;
    for (CaffeBlob blob : _blobs) mem += blob.memoryForward();
    return mem;
  }

/**
 * Get the memory required to store the blobs for the gradients in the
 * backward pass.
 *
 * @return The memory required for gradients in bytes
 */
  public long memoryBlobsBackward() {
    if (!_phase.equals(Caffe.Phase.TRAIN)) return 0;
    long mem = 0;
    for (CaffeBlob blob : _blobs) mem += blob.memoryBackward();
    return mem;
  }

/**
 * Get the total memory required by the network with or without cuDNN.
 *
 * @param cuDNN Pass true if you want to estimate the memory consumption with
 *   cuDNN enabled, false otherwise
 * @return The estimated memory consumption in bytes
 */
  public long memoryTotal(boolean cuDNN) {
    return memoryParameters() + memoryOther() + memoryOverhead(cuDNN) +
        memoryBlobsForward() + memoryBlobsBackward() + memorySolver();
  }

/**
 * Get the total memory required by the network with or without cuDNN when
 * using a validation set during training. Using a validation set is recommended
 * but produces an forward-pass memory overhead. The total consumption is still
 * lower than forward pass plus forward-backward pass, because the memory for
 * parameters is shared.
 *
 * @param cuDNN Pass true if you want to estimate the memory consumption with
 *   cuDNN enabled, false otherwise
 * @return The estimated memory consumption in bytes
 */
  public long memoryTotalWithValidation(boolean cuDNN) {
    return memoryTotal(cuDNN) + memoryOther() + memoryBlobsForward();
  }

/**
 * Print a complete memory breakdown including all sources of memory
 * consumption to standard output.
 *
 * @param cuDNN Pass true if you want to estimate the memory consumption with
 *   cuDNN enabled, false otherwise
 */
  public void printMemoryBreakdown(boolean cuDNN) {
    System.out.println(
        "Total memory used (" + (cuDNN ? "" : "no ") + "cuDNN) = " +
        (memoryTotal(cuDNN) / 1024 / 1024) +
        " MB <= " + (memoryParameters() / 1024 / 1024) +
        " MB (param) + " + (memoryBlobsForward() / 1024 / 1024) +
        " MB (data) + " + (memoryBlobsBackward() / 1024 / 1024) +
        " MB (gradient) + " + (memoryOverhead(cuDNN) / 1024 / 1024) +
        " MB (conv) + " + (memoryOther() / 1024 / 1024) +
        " MB (other) + " + (memorySolver() / 1024 / 1024) + " MB (solver)");
    if (_phase.equals(Caffe.Phase.TRAIN))
        System.out.println(
            "  Training with validation set requires " +
            (memoryTotalWithValidation(cuDNN) / 1024 / 1024) + " MB");
  }

  private Vector<NetworkLayer> _layers = new Vector<NetworkLayer>();
  private Vector<CaffeBlob> _blobs = new Vector<CaffeBlob>();
  private Vector<CaffeBlob> _outputBlobs = new Vector<CaffeBlob>();
  private final Caffe.Phase _phase;

}
