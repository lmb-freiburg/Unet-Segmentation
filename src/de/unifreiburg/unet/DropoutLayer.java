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

public class DropoutLayer extends NetworkLayer {

  public DropoutLayer(
      Caffe.LayerParameter layerParam, Net net, CaffeBlob[] in) {
    super(layerParam, net, in);
    long mem = 0;
    for (int i = 0; i < in.length; ++i) {
      if (layerParam.getTop(i).equals(in[i].name())) _out[i] = in[i];
      else _out[i] = new CaffeBlob(
          layerParam.getTop(i), in[i].shape(),
          this, true, in[i].gradientRequired());
    }
    for (CaffeBlob blob : in) blob.setOnGPU(true);

    // Random masks are unsigned int and the same size as the blobs
    for (CaffeBlob blob : in) mem += 4 * blob.count();

    _memOther = mem;
  }

  @Override
  public String layerTypeString() { return "DropoutLayer"; }

  @Override
  public long memoryOther() {
    return _memOther;
  }

  private final long _memOther;
}
