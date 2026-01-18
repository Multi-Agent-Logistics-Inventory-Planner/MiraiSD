export const getItemsSchema = {
  querystring: {
    type: 'object',
    properties: {
      locationType: {
        type: 'string',
        enum: ['box_bin', 'rack', 'cabinet', 'single_claw_machine', 'double_claw_machine', 'keychain_machine'],
      },
      category: { type: 'string' },
      includeForecasts: { type: 'boolean', default: false },
      limit: { type: 'integer', default: 50, minimum: 1, maximum: 100 },
      offset: { type: 'integer', default: 0, minimum: 0 },
    },
  },
};
