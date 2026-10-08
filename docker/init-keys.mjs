import { generateKeyPairSync, createPrivateKey, createPublicKey } from 'node:crypto';
import { existsSync, readFileSync, writeFileSync, chmodSync, chownSync } from 'node:fs';

// Keep keys across restarts. Only Auth mounts the private volume.
for (const purpose of ['access', 'refresh']) {
    const privatePath = `/keys/private/${purpose}-private.pem`;
    const publicPath = `/keys/public/${purpose}-public.pem`;
    if (!existsSync(privatePath)) {
        if (existsSync(publicPath)) {
            throw new Error(`Missing ${purpose} private key; restore the private volume from backup.`);
        }
        const { privateKey } = generateKeyPairSync('rsa', {
            modulusLength: 2048,
            privateKeyEncoding: { type: 'pkcs8', format: 'pem' },
            publicKeyEncoding: { type: 'spki', format: 'pem' },
        });
        writeFileSync(privatePath, privateKey, { mode: 0o600, flag: 'wx' });
    }
    const privateKey = createPrivateKey(readFileSync(privatePath));
    const publicKey = createPublicKey(privateKey).export({ type: 'spki', format: 'pem' });
    if (existsSync(publicPath) && readFileSync(publicPath, 'utf8') !== publicKey) {
        throw new Error(`${purpose} key pair does not match; restore matching volumes.`);
    }
    writeFileSync(publicPath, publicKey, { mode: 0o644 });
    chownSync(privatePath, 10001, 10001);
    chmodSync(privatePath, 0o600);
    chmodSync(publicPath, 0o644);
}
console.log('JWT key pairs ready.');
