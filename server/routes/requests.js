const express = require('express');
const { UpdateRequest, Device } = require('../models');
require('dotenv').config();

const router = express.Router();
const APP_API_KEY = process.env.APP_API_KEY || 'my-mobile-app-static-key';

/**
 * Middleware: allowApp
 * Restricts access to the mobile app using a static API key.
 */
function allowApp(req, res, next) {
    const key = req.headers['x-api-key'];
    if (key === APP_API_KEY) {
        return next();
    }
    return res.status(401).json({ detail: "Invalid API Key" });
}

/**
 * POST /api/requests
 * Mobile app uses this to submit a new content update request.
 */
router.post('/', allowApp, async (req, res) => {
    const { device_id, requested_by, new_content } = req.body;
    
    try {
        // Validate device existence
        const device = await Device.findByPk(device_id);
        if (!device) return res.status(404).json({ detail: "Device not found" });

        // Create the pending request
        const request = await UpdateRequest.create({
            device_id,
            requested_by,
            new_content,
            status: 'PENDING'
        });

        res.json({ 
            message: "Update request submitted for approval",
            request_id: request.id 
        });
    } catch (error) {
        res.status(500).json({ detail: error.message });
    }
});

/**
 * GET /api/requests/:id/status
 * Polling endpoint for the mobile app to check if a request was approved/rejected.
 */
router.get('/:id/status', allowApp, async (req, res) => {
    try {
        const request = await UpdateRequest.findByPk(req.params.id);
        if (!request) return res.status(404).json({ detail: "Request not found" });
        res.json({ id: request.id, status: request.status });
    } catch (error) {
        res.status(500).json({ detail: error.message });
    }
});

module.exports = router;
