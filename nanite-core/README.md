Nanite-Core
=============

This code re-implements the [DROID](https://github.com/digital-preservation/droid) core workflow so that is better suited to running from the CLI, or embedded, or within a Hadoop MapReduce job. Critically, this means being able to identify byte arrays or input streams without assuming they are file-backed (as DROID does).
