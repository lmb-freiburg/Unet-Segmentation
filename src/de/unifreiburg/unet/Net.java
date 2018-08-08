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

public class Net {

  public Net() {}

  public static Net createFromProto(
      Caffe.NetParameter netParam, String[] inputBlobNames,
      int[][] inputBlobShapes, Caffe.Phase phase)
      throws NotImplementedException, BlobException {

    Net net = new Net();

    Vector<CaffeBlob> blobs = new Vector<CaffeBlob>();
    if (inputBlobNames != null)
        net.addLayer(
            new DataLayer(
                UUID.randomUUID().toString(), net,
                inputBlobNames, inputBlobShapes));

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

        CaffeBlob[] in = new CaffeBlob[l.getBottomCount()];
        int blobIdx = 0;
        for (; blobIdx < l.getBottomCount(); ++blobIdx) {
          in[blobIdx] = net.findBlob(l.getBottom(blobIdx));
          if (in[blobIdx] == null) break;
        }
        if (blobIdx < l.getBottomCount()) continue;

        net.addLayer(NetworkLayer.createFromProto(l, net, in));
        allConnected = false;
      }
    }
    return net;
  }

  public String toString() {
    String res = "Net { \n";
    for (NetworkLayer layer : _layers) res += "  " + layer + "\n";
    res += "}";
    return res;
  }

  public long memoryConsumption(boolean cuDNN) {
    long mem = 0;
    for (CaffeBlob blob : _blobs) mem += 4 * blob.count();
    for (NetworkLayer layer : _layers) mem += layer.memoryConsumption(cuDNN);
    return mem;
  }

  public void addLayer(NetworkLayer layer) {
    _layers.add(layer);
    if (layer.inputBlobs() != null)
        for (CaffeBlob blob : layer.inputBlobs())
            if (_outputBlobs.contains(blob)) _outputBlobs.remove(blob);
    if (layer.outputBlobs() != null)
        for (CaffeBlob blob : layer.outputBlobs()) {
          if (!_outputBlobs.contains(blob)) _outputBlobs.add(blob);
          if (!_blobs.contains(blob)) _blobs.add(blob);
        }
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

}
