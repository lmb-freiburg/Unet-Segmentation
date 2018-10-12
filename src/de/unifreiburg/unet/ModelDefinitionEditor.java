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

import ij.plugin.PlugIn;

import java.io.File;

import caffe.Caffe;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.TextFormat.Parser;
import com.google.protobuf.TextFormat;

public class ModelDefinitionEditor implements PlugIn {

  private ModelDefinition _model = new ModelDefintion();
  private Caffe.NetParameter.Builder _netBuilder =
      Caffe.NetParameter.newBuilder();

  public ModelDefinitionEditor() {}

  public void fromModeldef(File modeldefFile) {
    _model.load(modeldefFile);
    _netBuilder = Caffe.NetParameter.newBuilder();
    TextFormat.getParser().merge(_model.modelPrototxt, netBuilder);
    update();
  }

  public void update() {}

  @Override
  public void run(String arg) {

    ModelDefinition model = new ModelDefinition();
    model.file = new File("modeldef.h5");
    model.id = "U-Net";
    model.name = "U-Net";
    model.description = "U-Net";
    model.inputBlobName = "data3";
    model.padding = "mirror";
    model.classNames = new String[] { "foreground", "background" };
    model.setElementSizeUm(new double[] { 1.0, 1.0 });

  }

};
