import asyncio
import json


def call(server, name, args=None):
    result = asyncio.run(server.call_tool(name, args or {}))
    if getattr(result, "structured_content", None) is not None:
        data = result.structured_content
    elif getattr(result, "structuredContent", None) is not None:
        data = result.structuredContent
    else:
        data = json.loads(result.content[0].text)
    return data.get("result", data) if isinstance(data, dict) and set(data) == {"result"} else data
