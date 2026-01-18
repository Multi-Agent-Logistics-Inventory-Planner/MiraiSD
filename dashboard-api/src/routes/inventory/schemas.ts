export const getSummarySchema = {
  response: {
    200: {
      type: 'object',
      properties: {
        totalItems: { type: 'integer' },
        totalQuantity: { type: 'integer' },
        atRiskCount: { type: 'integer' },
        criticalCount: { type: 'integer' },
        byLocation: {
          type: 'object',
          properties: {
            boxBins: { $ref: '#/definitions/locationStats' },
            racks: { $ref: '#/definitions/locationStats' },
            cabinets: { $ref: '#/definitions/locationStats' },
            singleClawMachines: { $ref: '#/definitions/locationStats' },
            doubleClawMachines: { $ref: '#/definitions/locationStats' },
            keychainMachines: { $ref: '#/definitions/locationStats' },
          },
        },
        lastUpdated: { type: 'string', format: 'date-time' },
      },
      definitions: {
        locationStats: {
          type: 'object',
          properties: {
            itemCount: { type: 'integer' },
            totalQuantity: { type: 'integer' },
          },
        },
      },
    },
  },
};
