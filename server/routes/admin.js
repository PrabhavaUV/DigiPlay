const express = require('express');
const { UpdateRequest, Device } = require('../models');
const { requireAdminRequire } = require('../auth');

const router = express.Router();

/**
 * GET /api/admin/requests
 * Fetches all update requests for administrative review.
 */
router.get('/requests', requireAdminRequire, async (req, res) => {
    try {
        const list = await UpdateRequest.findAll();
        res.json(list);
    } catch (error) {
        res.status(500).json({ status: 'error', message: error.message });
    }
});

/**
 * POST /api/admin/requests/:id/approve
 * Approves a pending request and broadcasts the update via MQTT.
 */
router.post('/requests/:id/approve', async (req, res) => {
    const reqId = req.params.id;
    const { admin_notes } = req.body || {};

    try {
        const updateReq = await UpdateRequest.findOne({ where: { id: reqId, status: 'PENDING' } });
        if (!updateReq) return res.status(404).json({ detail: "Request not found or already processed" });
        
        // Update request status
        updateReq.status = 'APPROVED';
        if (admin_notes) updateReq.admin_notes = admin_notes;
        await updateReq.save();

        // Update target device content
        const device = await Device.findByPk(updateReq.device_id);
        if (device) {
            device.current_content = updateReq.new_content;
            await device.save();

            // Broadcast new content to hardware via MQTT
            const aedes = req.app.get('mqtt');
            const topic = `digiplay/devices/${device.id}/content`;
            const payload = JSON.stringify({
                content: device.current_content,
                checksum: Date.now().toString().substring(0, 8)
            });
            aedes.publish({ topic, payload, qos: 1, retain: true });
        }
        
        res.redirect('/dashboard');
    } catch (error) {
        console.error('[Admin] Approval failed:', error);
        res.status(500).send("Internal Server Error");
    }
});

/**
 * POST /api/admin/requests/:id/reject
 * Rejects a pending request.
 */
router.post('/requests/:id/reject', async (req, res) => {
    const reqId = req.params.id;
    const { admin_notes } = req.body || {};

    try {
        const updateReq = await UpdateRequest.findOne({ where: { id: reqId, status: 'PENDING' } });
        if (!updateReq) return res.status(404).json({ detail: "Request not found or already processed" });
        
        updateReq.status = 'REJECTED';
        if (admin_notes) updateReq.admin_notes = admin_notes;
        await updateReq.save();
        
        res.redirect('/dashboard');
    } catch (error) {
        console.error('[Admin] Rejection failed:', error);
        res.status(500).send("Internal Server Error");
    }
});

module.exports = router;

