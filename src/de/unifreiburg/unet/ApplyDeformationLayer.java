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

public class ApplyDeformationLayer extends NetworkLayer {

  public ApplyDeformationLayer(
      Caffe.LayerParameter layerParam, Net net, CaffeBlob[] in)
      throws BlobException {
    super(layerParam, net, in);
    Caffe.ApplyDeformationParameter cp = layerParam.getApplyDeformationParam();
    String shapeFrom = cp.getOutputShapeFrom();
    long[] outShape = new long[in[0].shape().length];
    outShape[0] = in[0].nSamples();
    outShape[1] = in[0].nChannels();
    if (shapeFrom.equals("")) {
      for (int d = 2; d < outShape.length; ++d)
          outShape[d] = in[1].shape()[d - 1];
    }
    else {
      CaffeBlob fromBlob = net.findBlob(shapeFrom);
      if (fromBlob == null) throw new BlobException(
          "No input blob named " + shapeFrom +
          " to copy the shape from in ApplyDeformationLayer");
      for (int d = 2; d < outShape.length; ++d)
          outShape[d] = fromBlob.shape()[d];
    }
    _out[0] = new CaffeBlob(layerParam.getTop(0), outShape, this);
  }

}
