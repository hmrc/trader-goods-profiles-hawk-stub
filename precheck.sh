#!/usr/bin/env bash

sbt clean scalafmtAll scalafmtCheckAll coverage test it/test coverageReport