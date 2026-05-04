const express = require('express');
const crypto = require('crypto');
const { Device } = require('../models');
const { verifyDeviceToken } = require('../auth');

const router = express.Router();

router.get('/:id/content', async (req, res) => {
    const token = req.headers['x-device-token'];
    if (!token) return res.status(401).json({ detail: "Missing token" });

    const device = await Device.findByPk(req.params.id);
    if (!device) return res.status(404).json({ detail: "Device not found" });

    if (!verifyDeviceToken(token, device.device_token)) {
        return res.status(401).json({ detail: "Invalid token" });
    }

    device.last_seen = new Date();
    device.is_online = true;
    await device.save();

    const checksum = crypto.createHash('md5').update(device.current_content).digest('hex').substring(0, 8);
    res.json({
        device_id: device.id,
        content: device.current_content,
        updated_at: device.created_at,
        checksum
    });
});

module.exports = router;
