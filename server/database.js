const { Sequelize } = require('sequelize');
const path = require('path');
require('dotenv').config();

const dbUrl = process.env.DATABASE_URL || 'sqlite://./digiplay.db';
let sequelize;

if (dbUrl.startsWith('sqlite')) {
    sequelize = new Sequelize({
        dialect: 'sqlite',
        storage: path.join(__dirname, 'digiplay.db'),
        logging: false
    });
} else {
    sequelize = new Sequelize(dbUrl, {
        logging: false
    });
}

module.exports = sequelize;
