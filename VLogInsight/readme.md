## Scheme

We present the key modules of vLogInsight here.

### RAG

We introduce the two in-vehicle compliance standards, GB 44497-2024 and YD/T 3746-2020, as external knowledge sources for RAG to expand the knowledge boundaries of large language models in detecting private data within in-vehicle app logging sites.

### Few-shot

We present a subset of the 150 instances used for few-shot prompting to detect private data in in-vehicle app logging sites.

### Cus_FlowDroid

We customize FlowDroid to perform backward data-flow analysis on the private data detected in logging sites and leverage LLMs to verify whether sanitization operations (e.g., encryption, desensitization, obfuscation) are applied, thereby confirming actual privacy leakage through app logs.


