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

public class Net {

  public Net(Caffe.Phase phase) {
    _phase = phase;
  }

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

  public Caffe.Phase phase() {
    return _phase;
  }

  @Override
  public String toString() {
    String res = "Net (phase = " +
        (_phase.equals(Caffe.Phase.TRAIN) ? "TRAIN" : "TEST")+ ") { \n";
    for (NetworkLayer layer : _layers) res += "  " + layer + "\n";
    res += "}";
    return res;
  }

  public CaffeBlob[] outputBlobs() {
    return _outputBlobs.toArray(new CaffeBlob[_outputBlobs.size()]);
  }

  public long memoryParameters() {
    long mem = 0;
    for (NetworkLayer layer : _layers) mem += layer.memoryParameters();
    return mem;
  }

  public long memorySolver() {
    return (_phase.equals(Caffe.Phase.TRAIN)) ?
        3 * memoryParameters() : 0;
  }

  public long memoryOther() {
    long mem = 0;
    for (NetworkLayer layer : _layers) mem += layer.memoryOther();
    return mem;
  }

  public long memoryOverhead(boolean cuDNN) {
    long mem = 0;
    for (NetworkLayer layer : _layers) mem += layer.memoryOverhead(cuDNN);
    return mem;
  }

  public long memoryBlobsForward() {
    long mem = 0;
    for (CaffeBlob blob : _blobs) mem += blob.memoryForward();
    return mem;
  }

  public long memoryBlobsBackward() {
    if (!_phase.equals(Caffe.Phase.TRAIN)) return 0;
    long mem = 0;
    for (CaffeBlob blob : _blobs) mem += blob.memoryBackward();
    return mem;
  }

  public long memoryTotal(boolean cuDNN) {
    return memoryParameters() + memoryOther() + memoryOverhead(cuDNN) +
        memoryBlobsForward() + memoryBlobsBackward() + memorySolver();
  }

  public long memoryTotalWithValidation(boolean cuDNN) {
    return memoryTotal(cuDNN) + memoryOther() + memoryBlobsForward();
  }

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
            (memoryTotalWithValidation(cuDNN) /1024 / 1024) + " MB");
  }

  public void addLayer(NetworkLayer layer) {
    addLayer(layer, false);
  }

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

  public Vector<NetworkLayer> layers() {
    return _layers;
  }


  public NetworkLayer findLayer(String name) {
    for (NetworkLayer layer : _layers)
        if (layer.name().equals(name)) return layer;
    return null;
  }

  public CaffeBlob findBlob(String name) {
    for (CaffeBlob blob : _blobs) if (blob.name().equals(name)) return blob;
    return null;
  }

  private Vector<NetworkLayer> _layers = new Vector<NetworkLayer>();
  private Vector<CaffeBlob> _blobs = new Vector<CaffeBlob>();
  private Vector<CaffeBlob> _outputBlobs = new Vector<CaffeBlob>();
  private final Caffe.Phase _phase;

}
