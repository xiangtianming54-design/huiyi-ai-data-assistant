// 包装 Python 知识索引重建：node scripts/rebuild-chroma.js --mapping mapping.json
const { spawnSync } = require('child_process');
const path = require('path');

const pythonDir = path.resolve(__dirname, '..', 'python-service');
const args = ['-m', 'app.tools.rebuild_knowledge_index', ...process.argv.slice(2)];
const result = spawnSync('python', args, { cwd: pythonDir, stdio: 'inherit', shell: true });
process.exit(result.status === null ? 1 : result.status);
