const express = require('express');
const { Device } = require('../models');
const { requireAdminRequire, generateDeviceToken } = require('../auth');
require('dotenv').config();

const router = express.Router();
const APP_API_KEY = process.env.APP_API_KEY || 'my-mobile-app-static-key';

/**
 * Middleware: allowAdminOrApp
 * Allows access if either a valid Admin JWT is present OR the static Mobile App API Key.
 */
function allowAdminOrApp(req, res, next) {
    const key = req.headers['x-api-key'];
    if (key === APP_API_KEY) {
        return next();
    }
    return requireAdminRequire(req, res, next);
}

/**
 * GET /api/devices
 * Lists all registered devices.
 */
router.get('/', requireAdminRequire, async (req, res) => {
    const devices = await Device.findAll();
    res.json(devices);
});

/**
 * POST /api/devices
 * Registers a new device and generates its unique token.
 */
router.post('/', requireAdminRequire, async (req, res) => {
    const { name, description } = req.body;
    const { raw, hashed } = generateDeviceToken();
    const newDevice = await Device.create({
        name, description, device_token: hashed
    });
    const result = newDevice.toJSON();
    result.raw_token = raw;
    res.json(result);
});

/**
 * GET /api/devices/:id
 * Fetches details for a specific device. Used by the mobile app.
 */
router.get('/:id', allowAdminOrApp, async (req, res) => {
    const device = await Device.findByPk(req.params.id);
    if (!device) return res.status(404).json({ detail: "Device not found" });
    res.json(device);
});

/**
 * PUT /api/devices/:id
 * Updates device metadata.
 */
router.put('/:id', requireAdminRequire, async (req, res) => {
    const device = await Device.findByPk(req.params.id);
    if (!device) return res.status(404).json({ detail: "Device not found" });
    
    const { name, description } = req.body;
    if (name) device.name = name;
    if (description) device.description = description;
    
    await device.save();
    res.json(device);
});

/**
 * DELETE /api/devices/:id
 * Removes a device from the system.
 */
router.delete('/:id', requireAdminRequire, async (req, res) => {
    const device = await Device.findByPk(req.params.id);
    if (!device) return res.status(404).json({ detail: "Device not found" });
    await device.destroy();
    res.json({ message: "Device successfully removed" });
});

module.exports = router;

