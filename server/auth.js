const jwt = require('jsonwebtoken');
const bcrypt = require('bcrypt');
const crypto = require('crypto');
require('dotenv').config();

const SECRET_KEY = process.env.SECRET_KEY || "09d25e094faa6ca2556c818166b7a9563b93f7099f6f0f4caa6cf63b88e8d3e7";

async function hashPassword(password) {
    return await bcrypt.hash(password, 10);
}

async function verifyPassword(password, hash) {
    return await bcrypt.compare(password, hash);
}

function createAccessToken(data) {
    return jwt.sign(data, SECRET_KEY, { expiresIn: '24h' });
}

function verifyToken(token) {
    try {
        return jwt.verify(token, SECRET_KEY);
    } catch (e) {
        return null;
    }
}

async function requireAdminRequire(req, res, next) {
    const authHeader = req.headers.authorization;
    if (!authHeader || !authHeader.startsWith('Bearer ')) {
        return res.status(401).json({ detail: "Unauthorized" });
    }
    const token = authHeader.split(' ')[1];
    const payload = verifyToken(token);
    if (!payload || !payload.sub) {
        return res.status(401).json({ detail: "Invalid token" });
    }
    const { Admin } = require('./models');
    const admin = await Admin.findOne({ where: { username: payload.sub } });
    if (!admin) {
        return res.status(401).json({ detail: "Admin not found" });
    }
    req.admin = admin;
    next();
}

function generateDeviceToken() {
    const raw = crypto.randomBytes(24).toString('base64url');
    const hashed = crypto.createHash('sha256').update(raw).digest('hex');
    return { raw, hashed };
}

function verifyDeviceToken(raw, storedHash) {
    const computed = crypto.createHash('sha256').update(raw).digest('hex');
    return computed === storedHash;
}

module.exports = {
    hashPassword,
    verifyPassword,
    createAccessToken,
    verifyToken,
    requireAdminRequire,
    generateDeviceToken,
    verifyDeviceToken
};
