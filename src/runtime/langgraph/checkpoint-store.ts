import { MemorySaver } from "@langchain/langgraph";

export interface RuntimeCheckpointStore {
  checkpointer: MemorySaver;
}

export function createMemoryCheckpointStore(): RuntimeCheckpointStore {
  return {
    checkpointer: new MemorySaver()
  };
}
