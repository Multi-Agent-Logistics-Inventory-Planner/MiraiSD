export const getForecastByItemIdSchema = {
  params: {
    type: 'object',
    required: ['itemId'],
    properties: {
      itemId: { type: 'string', format: 'uuid' },
    },
  },
};

export const getAtRiskForecastsSchema = {
  querystring: {
    type: 'object',
    properties: {
      threshold: { type: 'integer', default: 7, minimum: 1, maximum: 365 },
      limit: { type: 'integer', default: 50, minimum: 1, maximum: 100 },
      offset: { type: 'integer', default: 0, minimum: 0 },
    },
  },
};
