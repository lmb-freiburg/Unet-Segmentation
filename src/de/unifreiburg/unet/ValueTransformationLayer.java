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

public class ValueTransformationLayer extends NetworkLayer {

  public ValueTransformationLayer(
      String name, Net net, CaffeBlob[] in, String topName)
      throws BlobException {
    super(name, net, in, new CaffeBlob[1]);

    if (net.findBlob(topName) != null) throw new BlobException(
        "In-place value transformation not implemented");

    _out[0] = new CaffeBlob(topName, in[0].shape(), this);

    _memPara = 4 * 2 * in[0].nChannels();
  }

  public static NetworkLayer createFromProto(
      Caffe.LayerParameter layerParam, Net net, CaffeBlob[] in)
      throws BlobException {
    return new ValueTransformationLayer(
        layerParam.getName(), net, in, layerParam.getTop(0));
  }

  @Override
  public String layerTypeString() { return "ValueTransformationLayer"; }

  @Override
  public long memoryConsumptionParameters() {
    return _memPara;
  }

  private final long _memPara;

}
