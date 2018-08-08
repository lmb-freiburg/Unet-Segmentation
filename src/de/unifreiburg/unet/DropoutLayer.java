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
      String name, Net net, CaffeBlob[] in, String[] topNames) {
    super(name, net, in, new CaffeBlob[in.length]);
    long mem = 0;
    for (int i = 0; i < in.length; ++i) {
      if (topNames[i].equals(in[i].name())) {
        _out[i] = in[i];
        // Even if the blobs have the same name, the operation is not in-place!
        // Since the blobs are not counted, add their required memory to the
        // layer's overhead
        mem += 4 * in[i].count();
      }
      else _out[i] = new CaffeBlob(topNames[i], in[i].shape(), this, true);
    }
    for (CaffeBlob blob : in) blob.setOnGPU(true);

    // Random masks are unsigned int and the same size as the blobs
    for (CaffeBlob blob : in) mem += 4 * blob.count();

    _memOverhead = mem;
  }

  public static NetworkLayer createFromProto(
      Caffe.LayerParameter layerParam, Net net, CaffeBlob[] in) {
    return new DropoutLayer(
        layerParam.getName(), net, in, layerParam.getTopList().toArray(
            new String[layerParam.getTopCount()]));
  }

  @Override
  public String layerTypeString() { return "DropoutLayer"; }

  @Override
  public long memoryOverhead(boolean cuDNN) {
    return _memOverhead;
  }

  private final long _memOverhead;
}
