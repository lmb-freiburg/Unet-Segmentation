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

import java.util.UUID;

public class SoftmaxWithLossLayer extends NetworkLayer {

  public SoftmaxWithLossLayer(
      Caffe.LayerParameter layerParam, Net net, CaffeBlob[] in) {
    super(layerParam, net, in);
    Caffe.LayerParameter.Builder lb = layerParam.newBuilder(layerParam);
    lb.clearTop();
    lb.addTop(UUID.randomUUID().toString());
    _softmaxLayer = new SoftmaxLayer(lb.build(), net, in);
    _out[0] = new CaffeBlob(
        layerParam.getTop(0), new long[] { 1 }, this, true, true);
    if (layerParam.getTopCount() > 1)
        _out[1] = new CaffeBlob(
            layerParam.getTop(1), in[0].shape(), this, true);
    for (CaffeBlob blob : in) blob.setOnGPU(true);
  }

  @Override
  public long memoryOther() {
    return _softmaxLayer.memoryOther() +
        4 * _softmaxLayer.outputBlobs()[0].count() +
        ((_softmaxLayer.outputBlobs().length > 1) ?
         4 * _softmaxLayer.outputBlobs()[1].count() : 0);
  }

  private final SoftmaxLayer _softmaxLayer;
}
