/*
 * This file is part of Koral.
 *
 * Koral is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Koral is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Leser General Public License
 * along with Koral.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Copyright 2016 Daniel Janke
 */
package de.uni_koblenz.west.koral.master.graph_cover_creator.impl;

import java.io.InputStream;
import java.util.Scanner;
import java.util.logging.Logger;

/**
 * Writes the output of a process to a {@link Logger}.
 * 
 * @author Daniel Janke &lt;danijankATuni-koblenz.de&gt;
 *
 */
public class LogPipedWriter extends Thread {

  private final InputStream input;

  private final Logger logger;

  public LogPipedWriter(InputStream inputStream, Logger logger) {
    input = inputStream;
    this.logger = logger;
  }

  @Override
  public void run() {
    try (Scanner sc = new Scanner(input);) {
      while (sc.hasNextLine()) {
        logger.finest(sc.nextLine());
      }
    }
  }

}
