export async function getLastSync(
  kv: KVNamespace,
  key: string,
): Promise<string | null> {
  return kv.get(key);
}

export async function setLastSync(
  kv: KVNamespace,
  key: string,
  value: string,
): Promise<void> {
  await kv.put(key, value);
}
